package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suggests a monthly limit for the categories that have none yet.
 *
 * Only the last three finished months are used (the month in progress is left out). The average
 * of a category is its total divided by the number of those months that hold some spending at
 * all, so a month where the app was not used does not lower the average. A category whose average
 * is under 5 is ignored. The suggestion is the average plus 10 %, rounded up to a round figure
 * (a multiple of 5 below 100, of 10 below 500, of 50 below 2000, of 100 above). At most 8
 * suggestions are returned, the biggest spending first. A category name longer than 100
 * characters is ignored because the category budgets refuse such names.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class CategoryBudgetSuggestionCalculator {

    public static final int WINDOW_MONTHS = 3;
    public static final int MAX_SUGGESTIONS = 8;
    public static final int MAX_CATEGORY_LENGTH = 100;

    static final BigDecimal MARGIN = new BigDecimal("1.10");
    static final BigDecimal MIN_AVERAGE = new BigDecimal("5");

    private CategoryBudgetSuggestionCalculator() {
    }

    public record Spend(String category, BigDecimal amount, LocalDate date) {
    }

    public record Suggestion(String category, BigDecimal averageSpent, BigDecimal suggestedAmount, int monthsUsed) {
    }

    public record Result(int basedOnMonths, List<Suggestion> suggestions) {
    }

    /**
     * The comparison key of a category: trimmed and lower case. existingCategories must use it too.
     */
    public static String key(String category) {
        return category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    }

    public static Result compute(List<Spend> spends, Set<String> existingCategories, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(WINDOW_MONTHS);
        YearMonth last = current.minusMonths(1);

        Map<String, Map<YearMonth, BigDecimal>> totals = new HashMap<>();
        Map<String, Map<String, Integer>> spellings = new HashMap<>();
        Set<YearMonth> activeMonths = new HashSet<>();

        if (spends != null) {
            for (Spend spend : spends) {
                if (spend == null || spend.amount() == null || spend.date() == null || spend.amount().signum() <= 0) {
                    continue;
                }

                YearMonth month = YearMonth.from(spend.date());

                if (month.isBefore(first) || month.isAfter(last)) {
                    continue;
                }

                activeMonths.add(month);

                String key = key(spend.category());

                if (key.isEmpty() || key.length() > MAX_CATEGORY_LENGTH) {
                    continue;
                }

                totals.computeIfAbsent(key, k -> new HashMap<>()).merge(month, spend.amount(), BigDecimal::add);
                spellings.computeIfAbsent(key, k -> new HashMap<>()).merge(spend.category().trim(), 1, Integer::sum);
            }
        }

        int basedOn = activeMonths.size();

        if (basedOn == 0) {
            return new Result(0, List.of());
        }

        List<Suggestion> found = new ArrayList<>();

        for (Map.Entry<String, Map<YearMonth, BigDecimal>> entry : totals.entrySet()) {
            if (existingCategories != null && existingCategories.contains(entry.getKey())) {
                continue;
            }

            BigDecimal total = BigDecimal.ZERO;

            for (BigDecimal amount : entry.getValue().values()) {
                total = total.add(amount);
            }

            BigDecimal average = total.divide(BigDecimal.valueOf(basedOn), 2, RoundingMode.HALF_UP);

            if (average.compareTo(MIN_AVERAGE) < 0) {
                continue;
            }

            found.add(new Suggestion(
                    spelling(spellings.get(entry.getKey()), entry.getKey()),
                    average,
                    roundUp(average.multiply(MARGIN)),
                    entry.getValue().size()
            ));
        }

        found.sort(Comparator
                .comparing(Suggestion::averageSpent).reversed()
                .thenComparing(Suggestion::category));

        if (found.size() > MAX_SUGGESTIONS) {
            found = new ArrayList<>(found.subList(0, MAX_SUGGESTIONS));
        }

        return new Result(basedOn, found);
    }

    public static BigDecimal roundUp(BigDecimal amount) {
        BigDecimal step;

        if (amount.compareTo(BigDecimal.valueOf(100)) < 0) {
            step = BigDecimal.valueOf(5);
        } else if (amount.compareTo(BigDecimal.valueOf(500)) < 0) {
            step = BigDecimal.valueOf(10);
        } else if (amount.compareTo(BigDecimal.valueOf(2000)) < 0) {
            step = BigDecimal.valueOf(50);
        } else {
            step = BigDecimal.valueOf(100);
        }

        return amount.divide(step, 0, RoundingMode.CEILING).multiply(step).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String spelling(Map<String, Integer> counts, String fallback) {
        String best = null;
        int bestCount = 0;

        if (counts != null) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                int count = entry.getValue();

                if (best == null || count > bestCount || (count == bestCount && entry.getKey().compareTo(best) < 0)) {
                    best = entry.getKey();
                    bestCount = count;
                }
            }
        }

        return best == null ? fallback : best;
    }
}
