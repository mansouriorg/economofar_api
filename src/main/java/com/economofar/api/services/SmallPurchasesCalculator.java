package com.economofar.api.services;

import com.economofar.api.dtos.SmallGroupResponse;
import com.economofar.api.dtos.SmallMonthResponse;
import com.economofar.api.dtos.SmallPurchasesResponse;

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
 * Adds up the small purchases of the last finished months (6 by default, from 3 to 12): the ones
 * whose amount is at most the threshold in euros (10 by default, from 1 to 100). The month in
 * progress is left out because it is not complete.
 *
 * The months before the first one with a ticket are not counted: trackedMonths starts at the
 * first month with a purchase of any size, and the averages and the yearly estimate use it.
 * The yearly estimate is twelve times the monthly average, and yearlySavingIfQuarterFewer is a quarter
 * of it. daysPerPurchase is the number of days from the first day of the first tracked month to
 * the end of the window, divided by the number of small purchases.
 *
 * Only purchases with an amount above zero are used. A merchant or a category is the same whatever
 * the case, the accents and the punctuation; an empty merchant is "Enseigne inconnue" and an empty
 * category is "Autre". The letters oe, ae and ss are the same as the ligatures that stand for
 * them. The name shown is the spelling used by most purchases (the first in alphabetical order
 * when several are used as often). The 5 biggest merchants and categories are returned, the
 * biggest first (then the one with more purchases, then the first in alphabetical order of the
 * name without case, accents and punctuation). The answer never depends on the order of the
 * purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class SmallPurchasesCalculator {

    public record Entry(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int DEFAULT_MONTHS = 6;
    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 12;

    public static final int DEFAULT_THRESHOLD = 10;
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = 100;

    static final int MAX_GROUPS = 5;

    static final String OTHER_CATEGORY_KEY = "autre";
    static final String OTHER_CATEGORY_NAME = "Autre";
    static final String UNKNOWN_MERCHANT_KEY = "enseigneinconnue";
    static final String UNKNOWN_MERCHANT_NAME = "Enseigne inconnue";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    private SmallPurchasesCalculator() {
    }

    public static int clampMonths(Integer requested) {
        if (requested == null) {
            return DEFAULT_MONTHS;
        }

        return Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, requested));
    }

    public static int clampThreshold(Integer requested) {
        if (requested == null) {
            return DEFAULT_THRESHOLD;
        }

        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, requested));
    }

    public static SmallPurchasesResponse analyse(
            List<Entry> entries,
            LocalDate today,
            Integer requestedMonths,
            Integer requestedThreshold
    ) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int months = clampMonths(requestedMonths);
        int threshold = clampThreshold(requestedThreshold);
        BigDecimal limit = BigDecimal.valueOf(threshold);

        YearMonth last = YearMonth.from(today).minusMonths(1);
        YearMonth first = last.minusMonths(months - 1);

        BigDecimal[] allTotals = zeros(months);
        int[] allCounts = new int[months];
        BigDecimal[] smallTotals = zeros(months);
        int[] smallCounts = new int[months];
        Map<String, Group> merchants = new HashMap<>();
        Map<String, Group> categories = new HashMap<>();

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

                allTotals[month] = allTotals[month].add(entry.amount());
                allCounts[month]++;

                if (entry.amount().compareTo(limit) > 0) {
                    continue;
                }

                smallTotals[month] = smallTotals[month].add(entry.amount());
                smallCounts[month]++;

                String merchantText = tidy(entry.merchant());
                String merchantKey = key(merchantText);

                if (merchantKey.isEmpty()) {
                    merchantKey = UNKNOWN_MERCHANT_KEY;
                    merchantText = UNKNOWN_MERCHANT_NAME;
                }

                merchants.computeIfAbsent(merchantKey, k -> new Group()).add(merchantText, entry.amount());

                String categoryText = tidy(entry.category());
                String categoryKey = key(categoryText);

                if (categoryKey.isEmpty()) {
                    categoryKey = OTHER_CATEGORY_KEY;
                    categoryText = OTHER_CATEGORY_NAME;
                }

                categories.computeIfAbsent(categoryKey, k -> new Group()).add(categoryText, entry.amount());
            }
        }

        int firstTracked = -1;

        for (int month = 0; month < months && firstTracked < 0; month++) {
            if (allCounts[month] > 0) {
                firstTracked = month;
            }
        }

        int trackedMonths = firstTracked < 0 ? 0 : months - firstTracked;

        BigDecimal totalAmount = sum(allTotals);
        BigDecimal smallTotal = sum(smallTotals);
        int purchaseCount = sum(allCounts);
        int smallCount = sum(smallCounts);

        BigDecimal smallAverage = null;
        BigDecimal daysPerPurchase = null;
        BigDecimal monthlyAverage = null;
        BigDecimal yearlyEstimate = null;
        BigDecimal yearlySavingIfQuarterFewer = null;

        if (smallCount > 0) {
            smallAverage = smallTotal.divide(BigDecimal.valueOf(smallCount), 2, RoundingMode.HALF_UP);
        }

        if (trackedMonths > 0) {
            BigDecimal tracked = BigDecimal.valueOf(trackedMonths);

            monthlyAverage = smallTotal.divide(tracked, 2, RoundingMode.HALF_UP);
            yearlyEstimate = smallTotal.multiply(TWELVE).divide(tracked, 2, RoundingMode.HALF_UP);
            yearlySavingIfQuarterFewer = smallTotal.multiply(THREE).divide(tracked, 2, RoundingMode.HALF_UP);

            if (smallCount > 0) {
                long days = ChronoUnit.DAYS.between(first.plusMonths(firstTracked).atDay(1), last.atEndOfMonth()) + 1;

                daysPerPurchase = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(smallCount), 1, RoundingMode.HALF_UP);
            }
        }

        List<String> monthKeys = new ArrayList<>();
        List<SmallMonthResponse> perMonth = new ArrayList<>();

        for (int month = 0; month < months; month++) {
            monthKeys.add(first.plusMonths(month).toString());
            perMonth.add(new SmallMonthResponse(smallCounts[month], money(smallTotals[month])));
        }

        return new SmallPurchasesResponse(
                first.toString(),
                last.toString(),
                months,
                threshold,
                monthKeys,
                trackedMonths,
                purchaseCount,
                money(totalAmount),
                smallCount,
                money(smallTotal),
                smallAverage,
                monthlyAverage,
                yearlyEstimate,
                yearlySavingIfQuarterFewer,
                percent(smallTotal, totalAmount),
                percent(BigDecimal.valueOf(smallCount), BigDecimal.valueOf(purchaseCount)),
                daysPerPurchase,
                perMonth,
                merchants.size(),
                top(merchants, smallTotal),
                categories.size(),
                top(categories, smallTotal)
        );
    }

    private record Row(String key, String name, int count, BigDecimal total) {
    }

    private static final class Group {
        final Map<String, Integer> spellings = new HashMap<>();
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;

        void add(String text, BigDecimal amount) {
            spellings.merge(text, 1, Integer::sum);
            count++;
            total = total.add(amount);
        }

        String name() {
            String best = null;
            int bestCount = -1;

            for (Map.Entry<String, Integer> spelling : spellings.entrySet()) {
                int votes = spelling.getValue();

                if (votes > bestCount || (votes == bestCount && spelling.getKey().compareTo(best) < 0)) {
                    best = spelling.getKey();
                    bestCount = votes;
                }
            }

            return best;
        }
    }

    private static List<SmallGroupResponse> top(Map<String, Group> groups, BigDecimal smallTotal) {
        List<Row> rows = new ArrayList<>();

        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            Group group = entry.getValue();

            rows.add(new Row(entry.getKey(), group.name(), group.count, group.total));
        }

        rows.sort(
                Comparator.comparing((Row r) -> r.total(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing((Row r) -> r.count(), Comparator.<Integer>reverseOrder())
                        .thenComparing((Row r) -> r.key())
        );

        List<SmallGroupResponse> result = new ArrayList<>();

        for (int i = 0; i < rows.size() && i < MAX_GROUPS; i++) {
            Row row = rows.get(i);

            result.add(new SmallGroupResponse(row.name(), row.count(), money(row.total()), percent(row.total(), smallTotal)));
        }

        return result;
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return null;
        }

        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(BigDecimal[] values) {
        BigDecimal total = BigDecimal.ZERO;

        for (BigDecimal value : values) {
            total = total.add(value);
        }

        return total;
    }

    private static int sum(int[] values) {
        int total = 0;

        for (int value : values) {
            total += value;
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
                .replace("\u0153", "oe")
                .replace("\u00E6", "ae")
                .replace("\u00DF", "ss")
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
