package com.economofar.api.services;

import com.economofar.api.dtos.RecurringExpenseResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds expenses that come back regularly: same shop, similar amount, steady rhythm.
 *
 * A shop is kept when it has at least 3 different purchase days, when most of the gaps
 * between two purchases are close to a weekly, bi-weekly, monthly or quarterly rhythm,
 * when most of the amounts are within 15 percent of the usual amount, and when the last
 * purchase is recent enough (the expense did not stop).
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class RecurringExpenseDetector {

    public record Entry(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    private enum Frequency {
        WEEKLY("weekly", 5, 9, "4.3333"),
        BIWEEKLY("biweekly", 12, 17, "2.1667"),
        MONTHLY("monthly", 26, 35, "1"),
        QUARTERLY("quarterly", 80, 100, "0.3333");

        private final String code;
        private final int minDays;
        private final int maxDays;
        private final BigDecimal perMonth;

        Frequency(String code, int minDays, int maxDays, String perMonth) {
            this.code = code;
            this.minDays = minDays;
            this.maxDays = maxDays;
            this.perMonth = new BigDecimal(perMonth);
        }

        boolean contains(int days) {
            return days >= minDays && days <= maxDays;
        }

        static Frequency forGap(int days) {
            for (Frequency frequency : values()) {
                if (frequency.contains(days)) {
                    return frequency;
                }
            }

            return null;
        }
    }

    private static final int MIN_OCCURRENCES = 3;
    private static final int MIN_CONTAINS_LENGTH = 5;
    private static final double MIN_SHARE = 0.6;
    private static final double AMOUNT_TOLERANCE = 0.15;
    private static final double STOPPED_AFTER_INTERVALS = 2.5;

    private static final Set<String> PLACEHOLDER_NAMES = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );

    private RecurringExpenseDetector() {
    }

    public static List<RecurringExpenseResponse> detect(List<Entry> entries, LocalDate today) {
        List<RecurringExpenseResponse> results = new ArrayList<>();

        if (entries == null || entries.isEmpty() || today == null) {
            return results;
        }

        for (List<Entry> group : groupByShop(entries)) {
            RecurringExpenseResponse found = analyse(group, today);

            if (found != null) {
                results.add(found);
            }
        }

        results.sort(
                Comparator.comparing(RecurringExpenseResponse::monthlyEstimate).reversed()
                        .thenComparing(RecurringExpenseResponse::merchantName)
        );

        return results;
    }

    private static List<List<Entry>> groupByShop(List<Entry> entries) {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();

        for (Entry entry : entries) {
            if (entry == null || entry.date() == null || entry.amount() == null || entry.amount().signum() <= 0) {
                continue;
            }

            String key = normalize(entry.merchant());

            if (key.isEmpty() || PLACEHOLDER_NAMES.contains(key)) {
                continue;
            }

            String groupKey = null;

            for (String existing : groups.keySet()) {
                if (sameShop(key, existing)) {
                    groupKey = existing;
                    break;
                }
            }

            groups.computeIfAbsent(groupKey == null ? key : groupKey, ignored -> new ArrayList<>()).add(entry);
        }

        return new ArrayList<>(groups.values());
    }

    private static boolean sameShop(String first, String second) {
        if (first.equals(second)) {
            return true;
        }

        return Math.min(first.length(), second.length()) >= MIN_CONTAINS_LENGTH
                && (first.contains(second) || second.contains(first));
    }

    private static RecurringExpenseResponse analyse(List<Entry> group, LocalDate today) {
        TreeMap<LocalDate, Entry> byDay = new TreeMap<>();

        for (Entry entry : group) {
            if (!entry.date().isAfter(today)) {
                byDay.merge(entry.date(), entry, (current, other) ->
                        other.amount().compareTo(current.amount()) > 0 ? other : current);
            }
        }

        if (byDay.size() < MIN_OCCURRENCES) {
            return null;
        }

        List<Entry> ordered = new ArrayList<>(byDay.values());
        List<Integer> gaps = new ArrayList<>();

        for (int index = 1; index < ordered.size(); index++) {
            gaps.add((int) ChronoUnit.DAYS.between(ordered.get(index - 1).date(), ordered.get(index).date()));
        }

        int medianGap = medianInt(gaps);
        Frequency frequency = Frequency.forGap(medianGap);

        if (frequency == null) {
            return null;
        }

        long gapsInRange = gaps.stream().filter(frequency::contains).count();

        if (gapsInRange < 2 || gapsInRange < Math.ceil(gaps.size() * MIN_SHARE)) {
            return null;
        }

        List<BigDecimal> amounts = new ArrayList<>();

        for (Entry entry : ordered) {
            amounts.add(entry.amount());
        }

        BigDecimal typical = medianAmount(amounts);

        if (typical.signum() <= 0) {
            return null;
        }

        BigDecimal tolerance = typical.multiply(BigDecimal.valueOf(AMOUNT_TOLERANCE));
        long similar = amounts.stream()
                .filter(amount -> amount.subtract(typical).abs().compareTo(tolerance) <= 0)
                .count();

        if (similar < Math.ceil(amounts.size() * MIN_SHARE)) {
            return null;
        }

        Entry last = ordered.get(ordered.size() - 1);
        long daysSinceLast = ChronoUnit.DAYS.between(last.date(), today);

        if (daysSinceLast > Math.round(medianGap * STOPPED_AFTER_INTERVALS)) {
            return null;
        }

        LocalDate nextExpected = nextDate(last.date(), frequency, medianGap);

        BigDecimal monthlyEstimate = typical
                .multiply(frequency.perMonth)
                .setScale(2, RoundingMode.HALF_UP);

        return new RecurringExpenseResponse(
                displayName(last, ordered),
                lastCategory(ordered),
                typical.setScale(2, RoundingMode.HALF_UP),
                ordered.size(),
                frequency.code,
                medianGap,
                last.date().toString(),
                nextExpected.toString(),
                ChronoUnit.DAYS.between(today, nextExpected),
                monthlyEstimate
        );
    }

    private static LocalDate nextDate(LocalDate last, Frequency frequency, int medianGap) {
        return switch (frequency) {
            case MONTHLY -> last.plusMonths(1);
            case QUARTERLY -> last.plusMonths(3);
            default -> last.plusDays(medianGap);
        };
    }

    private static String displayName(Entry last, List<Entry> ordered) {
        if (last.merchant() != null && !last.merchant().isBlank()) {
            return last.merchant().trim();
        }

        for (int index = ordered.size() - 1; index >= 0; index--) {
            String merchant = ordered.get(index).merchant();

            if (merchant != null && !merchant.isBlank()) {
                return merchant.trim();
            }
        }

        return "";
    }

    private static String lastCategory(List<Entry> ordered) {
        for (int index = ordered.size() - 1; index >= 0; index--) {
            String category = ordered.get(index).category();

            if (category != null && !category.isBlank()) {
                return category.trim();
            }
        }

        return null;
    }

    private static int medianInt(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return (int) Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
    }

    private static BigDecimal medianAmount(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
