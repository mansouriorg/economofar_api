package com.economofar.api.services;

import com.economofar.api.dtos.UnusualPurchaseResponse;
import com.economofar.api.dtos.UnusualPurchasesResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the purchases of the last days that are far above what the user usually spends.
 *
 * Each purchase of the period is compared with the purchases of the 180 days before its date (the
 * ones of the same day are not used). When the same shop has at least four of them, the comparison
 * is with that shop; otherwise, when the same category has at least six, with that category;
 * otherwise the purchase is not compared. A shop or a category is the same whatever the case, the
 * accents and the punctuation, and a shop or a category that is not known is never a reference.
 *
 * A purchase is flagged when all three conditions hold:
 * - it is at least 3 times the median of the reference purchases;
 * - it is above the 90th percentile of the reference purchases (nearest rank), so that a shop
 *   where big and small purchases both happen does not flag the big ones;
 * - it is at least 1.5 times the median of all the purchases of the 180 days before, so that a
 *   purchase that is small next to the others is not flagged.
 *
 * Only purchases with an amount above zero are used (a purchase dated after today is ignored; the
 * service counts a purchase dated tomorrow as today). The answer never depends on the order of
 * the purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class UnusualPurchaseCalculator {

    public record Purchase(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 90;

    static final int LOOKBACK_DAYS = 180;
    static final int MIN_MERCHANT_SAMPLES = 4;
    static final int MIN_CATEGORY_SAMPLES = 6;
    static final int MAX_FLAGGED = 10;

    static final BigDecimal USUAL_FACTOR = new BigDecimal("3");
    static final BigDecimal OVERALL_FACTOR = new BigDecimal("1.5");

    static final String UNKNOWN_MERCHANT = "Commerçant inconnu";
    static final String UNKNOWN_CATEGORY = "Autre";

    private static final Set<String> UNKNOWN_MERCHANT_KEYS = Set.of(
            "commercantinconnu",
            "magasininconnu",
            "analyseencours",
            "inconnu"
    );

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private UnusualPurchaseCalculator() {
    }

    public static int clampDays(int days) {
        return Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
    }

    /** The first day of the period: {@code days} days, today included. */
    public static LocalDate windowStart(LocalDate today, int days) {
        return today.minusDays(clampDays(days) - 1L);
    }

    /** The first day of the purchases to read: the period and the history before it. */
    public static LocalDate fetchStart(LocalDate today, int days) {
        return windowStart(today, days).minusDays(LOOKBACK_DAYS);
    }

    public static UnusualPurchasesResponse analyse(List<Purchase> purchases, LocalDate today, int days) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        int period = clampDays(days);
        LocalDate from = windowStart(today, period);

        List<Clean> all = clean(purchases, fetchStart(today, period), today);

        Map<String, List<Clean>> byMerchant = new HashMap<>();
        Map<String, List<Clean>> byCategory = new HashMap<>();

        for (Clean item : all) {
            if (!item.merchantKey().isEmpty()) {
                byMerchant.computeIfAbsent(item.merchantKey(), k -> new ArrayList<>()).add(item);
            }

            if (!item.categoryKey().isEmpty()) {
                byCategory.computeIfAbsent(item.categoryKey(), k -> new ArrayList<>()).add(item);
            }
        }

        int examined = 0;
        int compared = 0;
        List<Flagged> flagged = new ArrayList<>();

        for (Clean candidate : all) {
            if (candidate.date().isBefore(from)) {
                continue;
            }

            examined++;

            LocalDate since = candidate.date().minusDays(LOOKBACK_DAYS);

            List<BigDecimal> reference = null;
            String basis = null;

            if (!candidate.merchantKey().isEmpty()) {
                List<BigDecimal> sameMerchant = amountsBefore(byMerchant.get(candidate.merchantKey()), since, candidate.date());

                if (sameMerchant.size() >= MIN_MERCHANT_SAMPLES) {
                    reference = sameMerchant;
                    basis = "merchant";
                }
            }

            if (reference == null && !candidate.categoryKey().isEmpty()) {
                List<BigDecimal> sameCategory = amountsBefore(byCategory.get(candidate.categoryKey()), since, candidate.date());

                if (sameCategory.size() >= MIN_CATEGORY_SAMPLES) {
                    reference = sameCategory;
                    basis = "category";
                }
            }

            if (reference == null) {
                continue;
            }

            compared++;

            List<BigDecimal> sortedReference = sorted(reference);
            BigDecimal usual = middle(sortedReference);

            boolean farAboveUsual = candidate.amount().compareTo(usual.multiply(USUAL_FACTOR)) >= 0;
            boolean aboveTopTenth = farAboveUsual && candidate.amount().compareTo(topTenth(sortedReference)) > 0;
            boolean unusual = aboveTopTenth
                    && candidate.amount().compareTo(middle(sorted(amountsBefore(all, since, candidate.date()))).multiply(OVERALL_FACTOR)) >= 0;

            if (unusual) {
                flagged.add(new Flagged(candidate, usual, basis, reference.size()));
            }
        }

        flagged.sort(
                Comparator.comparing((Flagged f) -> f.candidate.amount().divide(f.usual, 6, RoundingMode.HALF_UP)).reversed()
                        .thenComparing((Flagged f) -> f.candidate.amount(), Comparator.reverseOrder())
                        .thenComparing((Flagged f) -> f.candidate.date(), Comparator.reverseOrder())
                        .thenComparing((Flagged f) -> f.candidate.merchantKey())
                        .thenComparing((Flagged f) -> f.candidate.merchant())
                        .thenComparing((Flagged f) -> f.candidate.category())
        );

        List<UnusualPurchaseResponse> shown = new ArrayList<>();

        for (Flagged item : flagged) {
            if (shown.size() >= MAX_FLAGGED) {
                break;
            }

            shown.add(new UnusualPurchaseResponse(
                    item.candidate.date().toString(),
                    item.candidate.merchant(),
                    item.candidate.category(),
                    money(item.candidate.amount()),
                    money(item.usual),
                    item.candidate.amount().divide(item.usual, 1, RoundingMode.HALF_UP),
                    item.basis,
                    item.samples
            ));
        }

        return new UnusualPurchasesResponse(
                period,
                from.toString(),
                today.toString(),
                examined,
                compared,
                flagged.size(),
                shown
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Clean(String merchant, String merchantKey, String category, String categoryKey, BigDecimal amount, LocalDate date) {
    }

    private record Flagged(Clean candidate, BigDecimal usual, String basis, int samples) {
    }

    private static List<Clean> clean(List<Purchase> purchases, LocalDate lowerBound, LocalDate today) {
        List<Clean> result = new ArrayList<>();

        if (purchases == null) {
            return result;
        }

        for (Purchase purchase : purchases) {
            if (purchase == null
                    || purchase.date() == null
                    || purchase.amount() == null
                    || purchase.amount().signum() <= 0
                    || purchase.date().isBefore(lowerBound)
                    || purchase.date().isAfter(today)) {
                continue;
            }

            String merchantText = tidy(purchase.merchant());
            String merchantKey = key(merchantText);
            boolean knownMerchant = !merchantKey.isEmpty() && !UNKNOWN_MERCHANT_KEYS.contains(merchantKey);

            String categoryText = tidy(purchase.category());
            String categoryKey = key(categoryText);
            boolean knownCategory = !categoryKey.isEmpty() && !categoryKey.equals(key(UNKNOWN_CATEGORY));

            result.add(new Clean(
                    knownMerchant ? merchantText : UNKNOWN_MERCHANT,
                    knownMerchant ? merchantKey : "",
                    categoryKey.isEmpty() ? UNKNOWN_CATEGORY : categoryText,
                    knownCategory ? categoryKey : "",
                    purchase.amount(),
                    purchase.date()
            ));
        }

        return result;
    }

    /** The amounts of the purchases dated from {@code since} (included) to {@code before} (excluded). */
    private static List<BigDecimal> amountsBefore(List<Clean> items, LocalDate since, LocalDate before) {
        List<BigDecimal> amounts = new ArrayList<>();

        if (items == null) {
            return amounts;
        }

        for (Clean item : items) {
            if (!item.date().isBefore(since) && item.date().isBefore(before)) {
                amounts.add(item.amount());
            }
        }

        return amounts;
    }

    private static List<BigDecimal> sorted(List<BigDecimal> amounts) {
        List<BigDecimal> copy = new ArrayList<>(amounts);
        copy.sort(Comparator.naturalOrder());

        return copy;
    }

    /** The middle of a sorted list; the mean of the two middle values when there is an even number of them. */
    static BigDecimal middle(List<BigDecimal> sorted) {
        int size = sorted.size();

        if (size == 0) {
            return BigDecimal.ZERO;
        }

        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }

        return sorted.get(size / 2 - 1).add(sorted.get(size / 2)).divide(TWO);
    }

    /** The 90th percentile of a sorted list, nearest rank: the value at position ceil(0.9 x n). */
    static BigDecimal topTenth(List<BigDecimal> sorted) {
        int rank = (9 * sorted.size() + 9) / 10;

        return sorted.get(Math.max(rank, 1) - 1);
    }

    private static String tidy(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String key(String name) {
        return RecurringExpenseDetector.normalize(name);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
