package com.economofar.api.services;

import com.economofar.api.dtos.CategoryTrendResponse;
import com.economofar.api.dtos.CategoryTrendsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Looks at how the spending of each category moved over the last finished months (6 by default,
 * from 3 to 12). The month in progress is left out because it is not complete.
 *
 * The months before the first one with a ticket are not counted as months without spending:
 * trackedMonths starts at the first month with a ticket. The trend of a category compares its
 * spending over the most recent months with the same number of months before them: that number
 * is half of the tracked months, at most 3, and nothing is compared with less than 2 tracked
 * months. A change is only called UP or DOWN when it is at least 15 % and at least 5.00 per
 * month (the percentage being the one given, rounded to one decimal); otherwise it is STABLE. A category with nothing spent in the earlier months is NEW, one
 * with nothing spent in the recent months is GONE.
 *
 * Only purchases with an amount above zero are used. A category is the same whatever the case, the
 * accents and the punctuation; an empty category is "Autre". The name shown is the spelling used
 * by most purchases (the first in alphabetical order when several are used as often). With more
 * than 9 categories the 8 biggest are kept and the others are merged into "Autres catégories".
 * The answer never depends on the order of the purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class CategoryTrendsCalculator {

    public record Entry(String category, BigDecimal amount, LocalDate date) {
    }

    public static final int DEFAULT_MONTHS = 6;
    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 12;

    static final int MAX_COMPARE_MONTHS = 3;
    static final int MAX_ROWS = 9;
    static final int KEPT_ROWS = 8;

    static final String OTHER_KEY = "autre";
    static final String OTHER_NAME = "Autre";
    static final String MERGED_NAME = "Autres catégories";
    static final String OVERALL_NAME = "Toutes les catégories";

    static final BigDecimal MIN_CHANGE_PER_MONTH = new BigDecimal("5.00");
    static final BigDecimal MIN_CHANGE_PERCENT = new BigDecimal("15");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CategoryTrendsCalculator() {
    }

    public static int clampMonths(Integer requested) {
        if (requested == null) {
            return DEFAULT_MONTHS;
        }

        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    public static CategoryTrendsResponse analyse(List<Entry> entries, LocalDate today, Integer requestedMonths) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);
        YearMonth last = YearMonth.from(today).minusMonths(1);
        YearMonth first = last.minusMonths(months - 1);

        BigDecimal[] overall = zeros(months);
        boolean[] seen = new boolean[months];
        Map<String, Group> groups = new HashMap<>();

        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.amount() == null || entry.date() == null) {
                    continue;
                }

                if (entry.amount().signum() <= 0) {
                    continue;
                }

                long index = first.until(YearMonth.from(entry.date()), ChronoUnit.MONTHS);

                if (index < 0 || index >= months) {
                    continue;
                }

                int month = (int) index;

                overall[month] = overall[month].add(entry.amount());
                seen[month] = true;

                String text = tidy(entry.category());
                String key = key(text);

                if (key.isEmpty()) {
                    key = OTHER_KEY;
                    text = OTHER_NAME;
                }

                groups.computeIfAbsent(key, k -> new Group(months)).add(text, month, entry.amount());
            }
        }

        int firstTracked = -1;

        for (int month = 0; month < months && firstTracked < 0; month++) {
            if (seen[month]) {
                firstTracked = month;
            }
        }

        int trackedMonths = firstTracked < 0 ? 0 : months - firstTracked;
        int compareMonths = Math.min(MAX_COMPARE_MONTHS, trackedMonths / 2);

        List<Row> rows = new ArrayList<>();

        for (Group group : groups.values()) {
            rows.add(new Row(group.name(), group.totals));
        }

        rows.sort(
                Comparator.comparing((Row r) -> r.total(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Row r) -> r.name())
        );

        int categoryCount = rows.size();

        if (rows.size() > MAX_ROWS) {
            BigDecimal[] merged = zeros(months);

            for (Row row : rows.subList(KEPT_ROWS, rows.size())) {
                for (int month = 0; month < months; month++) {
                    merged[month] = merged[month].add(row.totals()[month]);
                }
            }

            rows = new ArrayList<>(rows.subList(0, KEPT_ROWS));
            rows.add(new Row(MERGED_NAME, merged));
        }

        List<CategoryTrendResponse> categories = new ArrayList<>();

        for (Row row : rows) {
            categories.add(trend(row.name(), row.totals(), trackedMonths, compareMonths));
        }

        List<String> monthKeys = new ArrayList<>();

        for (int month = 0; month < months; month++) {
            monthKeys.add(first.plusMonths(month).toString());
        }

        return new CategoryTrendsResponse(
                first.toString(),
                last.toString(),
                months,
                monthKeys,
                trackedMonths,
                compareMonths,
                trend(OVERALL_NAME, overall, trackedMonths, compareMonths),
                categoryCount,
                categories
        );
    }

    private static CategoryTrendResponse trend(String name, BigDecimal[] totals, int trackedMonths, int compareMonths) {
        int months = totals.length;
        BigDecimal total = sum(totals, 0, months);

        List<BigDecimal> monthly = new ArrayList<>();

        for (BigDecimal value : totals) {
            monthly.add(money(value));
        }

        BigDecimal monthlyAverage = trackedMonths > 0
                ? total.divide(BigDecimal.valueOf(trackedMonths), 2, RoundingMode.HALF_UP)
                : null;

        if (compareMonths < 1) {
            return new CategoryTrendResponse(name, monthly, money(total), monthlyAverage, null, null, null, "NONE");
        }

        BigDecimal recent = sum(totals, months - compareMonths, months);
        BigDecimal earlier = sum(totals, months - 2 * compareMonths, months - compareMonths);
        BigDecimal count = BigDecimal.valueOf(compareMonths);
        BigDecimal difference = recent.subtract(earlier);

        BigDecimal changePercent = earlier.signum() > 0
                ? difference.multiply(HUNDRED).divide(earlier, 1, RoundingMode.HALF_UP)
                : null;

        String trend;

        if (recent.signum() == 0 && earlier.signum() == 0) {
            trend = "NONE";
        } else if (difference.abs().compareTo(MIN_CHANGE_PER_MONTH.multiply(count)) < 0) {
            trend = "STABLE";
        } else if (earlier.signum() == 0) {
            trend = "NEW";
        } else if (recent.signum() == 0) {
            trend = "GONE";
        } else if (changePercent.abs().compareTo(MIN_CHANGE_PERCENT) >= 0) {
            trend = difference.signum() > 0 ? "UP" : "DOWN";
        } else {
            trend = "STABLE";
        }

        return new CategoryTrendResponse(
                name,
                monthly,
                money(total),
                monthlyAverage,
                recent.divide(count, 2, RoundingMode.HALF_UP),
                earlier.divide(count, 2, RoundingMode.HALF_UP),
                changePercent,
                trend
        );
    }

    private record Row(String name, BigDecimal[] totals) {
        BigDecimal total() {
            return sum(totals, 0, totals.length);
        }
    }

    private static final class Group {
        final Map<String, Integer> spellings = new HashMap<>();
        final BigDecimal[] totals;

        Group(int months) {
            totals = zeros(months);
        }

        void add(String text, int month, BigDecimal amount) {
            spellings.merge(text, 1, Integer::sum);
            totals[month] = totals[month].add(amount);
        }

        String name() {
            String best = null;
            int bestCount = -1;

            for (Map.Entry<String, Integer> spelling : spellings.entrySet()) {
                int count = spelling.getValue();

                if (count > bestCount || (count == bestCount && spelling.getKey().compareTo(best) < 0)) {
                    best = spelling.getKey();
                    bestCount = count;
                }
            }

            return best;
        }
    }

    private static BigDecimal sum(BigDecimal[] values, int from, int to) {
        BigDecimal total = BigDecimal.ZERO;

        for (int i = from; i < to; i++) {
            total = total.add(values[i]);
        }

        return total;
    }

    private static BigDecimal[] zeros(int size) {
        BigDecimal[] values = new BigDecimal[size];

        for (int i = 0; i < size; i++) {
            values[i] = BigDecimal.ZERO;
        }

        return values;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String tidy(String value) {
        return value == null ? "" : Normalizer.normalize(value.replaceAll("\\p{Cf}+", "").replaceAll("[\\p{Z}\\s]+", " ").trim(), Normalizer.Form.NFC);
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\u03C2', '\u03C3')
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
