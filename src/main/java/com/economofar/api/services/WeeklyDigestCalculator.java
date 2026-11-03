package com.economofar.api.services;

import com.economofar.api.dtos.DigestCategoryResponse;
import com.economofar.api.dtos.DigestDayResponse;
import com.economofar.api.dtos.DigestMerchantResponse;
import com.economofar.api.dtos.DigestPurchaseResponse;
import com.economofar.api.dtos.WeeklyDigestResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary of the last 7 days (today included) compared with the 7 days before.
 *
 * Only receipts with a date in these 14 days and an amount above zero are used. A shop or a
 * category is the same whatever the case, the accents and the punctuation; the spelling shown,
 * everywhere in the answer, is the one of the first receipt of the 14 days in date order, so the
 * answer does not depend on the order in which the receipts are given. Names that tie are put in
 * alphabetical order without looking at the case or the accents. A week is "stable" when it is
 * less than 5 percent away from the week before, measured on the exact amounts.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class WeeklyDigestCalculator {

    public record Purchase(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public static final String TREND_UP = "up";
    public static final String TREND_DOWN = "down";
    public static final String TREND_STABLE = "stable";
    public static final String TREND_NEW = "new";
    public static final String TREND_EMPTY = "empty";

    static final int DAYS = 7;
    static final int MAX_CATEGORIES = 5;
    static final BigDecimal STABLE_BELOW = new BigDecimal("5.0");
    static final String UNKNOWN_CATEGORY = "Autre";
    static final String UNKNOWN_MERCHANT = "Commerçant inconnu";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private WeeklyDigestCalculator() {
    }

    private record Clean(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    private static final class Group {
        final String name;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        int visits = 0;

        Group(String name) {
            this.name = name;
        }
    }

    public static WeeklyDigestResponse compute(List<Purchase> purchases, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        LocalDate from = today.minusDays(DAYS - 1);
        LocalDate previousFrom = today.minusDays(2L * DAYS - 1);

        List<Clean> clean = new ArrayList<>();

        if (purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase == null
                        || purchase.date() == null
                        || purchase.amount() == null
                        || purchase.amount().signum() <= 0
                        || purchase.date().isBefore(previousFrom)
                        || purchase.date().isAfter(today)) {
                    continue;
                }

                clean.add(new Clean(
                        label(purchase.merchant(), UNKNOWN_MERCHANT),
                        label(purchase.category(), UNKNOWN_CATEGORY),
                        purchase.amount(),
                        purchase.date()
                ));
            }
        }

        clean.sort(
                Comparator.comparing(Clean::date)
                        .thenComparing(Clean::amount, Comparator.reverseOrder())
                        .thenComparing(Clean::merchant)
                        .thenComparing(Clean::category)
        );

        Map<String, String> merchantNames = new LinkedHashMap<>();

        for (Clean item : clean) {
            merchantNames.putIfAbsent(key(item.merchant()), item.merchant());
        }

        BigDecimal[] dayTotals = new BigDecimal[DAYS];
        int[] dayCounts = new int[DAYS];

        for (int i = 0; i < DAYS; i++) {
            dayTotals[i] = BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal previousTotal = BigDecimal.ZERO;
        int ticketCount = 0;
        int previousTicketCount = 0;

        Map<String, Group> categories = new LinkedHashMap<>();
        Map<String, Group> merchants = new LinkedHashMap<>();
        Clean biggest = null;

        for (Clean raw : clean) {
            Clean item = new Clean(
                    merchantNames.get(key(raw.merchant())),
                    raw.category(),
                    raw.amount(),
                    raw.date()
            );

            boolean current = !item.date().isBefore(from);

            Group category = categories.computeIfAbsent(key(item.category()), k -> new Group(item.category()));

            if (current) {
                int index = (int) ChronoUnit.DAYS.between(from, item.date());
                dayTotals[index] = dayTotals[index].add(item.amount());
                dayCounts[index]++;

                total = total.add(item.amount());
                ticketCount++;

                category.current = category.current.add(item.amount());

                Group merchant = merchants.computeIfAbsent(key(item.merchant()), k -> new Group(item.merchant()));
                merchant.current = merchant.current.add(item.amount());
                merchant.visits++;

                if (biggest == null || isBigger(item, biggest)) {
                    biggest = item;
                }
            } else {
                previousTotal = previousTotal.add(item.amount());
                previousTicketCount++;

                category.previous = category.previous.add(item.amount());
            }
        }

        List<DigestDayResponse> days = new ArrayList<>();
        int noSpendDays = 0;

        for (int i = 0; i < DAYS; i++) {
            days.add(new DigestDayResponse(
                    from.plusDays(i).toString(),
                    money(dayTotals[i]),
                    dayCounts[i]
            ));

            if (dayCounts[i] == 0) {
                noSpendDays++;
            }
        }

        String trend;
        BigDecimal changePercent = null;

        if (previousTotal.signum() > 0) {
            changePercent = total.subtract(previousTotal)
                    .multiply(HUNDRED)
                    .divide(previousTotal, 1, RoundingMode.HALF_UP);

            BigDecimal difference = total.subtract(previousTotal);

            if (difference.abs().multiply(HUNDRED).compareTo(previousTotal.multiply(STABLE_BELOW)) < 0) {
                trend = TREND_STABLE;
            } else if (difference.signum() > 0) {
                trend = TREND_UP;
            } else {
                trend = TREND_DOWN;
            }
        } else {
            trend = total.signum() > 0 ? TREND_NEW : TREND_EMPTY;
        }

        List<Group> shown = new ArrayList<>();

        for (Group group : categories.values()) {
            if (group.current.signum() > 0) {
                shown.add(group);
            }
        }

        shown.sort(
                Comparator.comparing((Group g) -> g.current).reversed()
                        .thenComparing((a, b) -> byName(a.name, b.name))
        );

        List<DigestCategoryResponse> topCategories = new ArrayList<>();

        for (Group group : shown) {
            if (topCategories.size() >= MAX_CATEGORIES) {
                break;
            }

            topCategories.add(categoryResponse(group, total));
        }

        DigestCategoryResponse biggestChange = null;

        if (previousTotal.signum() > 0) {
            Group best = null;

            for (Group group : categories.values()) {
                BigDecimal change = group.current.subtract(group.previous);

                if (change.signum() == 0) {
                    continue;
                }

                if (best == null || isBiggerChange(group, best)) {
                    best = group;
                }
            }

            if (best != null) {
                biggestChange = categoryResponse(best, total);
            }
        }

        Group topMerchantGroup = null;

        for (Group group : merchants.values()) {
            if (topMerchantGroup == null || isBetterMerchant(group, topMerchantGroup)) {
                topMerchantGroup = group;
            }
        }

        DigestMerchantResponse topMerchant = topMerchantGroup == null
                ? null
                : new DigestMerchantResponse(topMerchantGroup.name, money(topMerchantGroup.current), topMerchantGroup.visits);

        DigestPurchaseResponse biggestPurchase = biggest == null
                ? null
                : new DigestPurchaseResponse(biggest.merchant(), money(biggest.amount()), biggest.date().toString());

        return new WeeklyDigestResponse(
                from.toString(),
                today.toString(),
                money(total),
                money(previousTotal),
                ticketCount,
                previousTicketCount,
                total.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP),
                changePercent,
                trend,
                noSpendDays,
                days,
                topCategories,
                topMerchant,
                biggestPurchase,
                biggestChange
        );
    }

    private static DigestCategoryResponse categoryResponse(Group group, BigDecimal total) {
        BigDecimal share = total.signum() > 0
                ? group.current.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1);

        return new DigestCategoryResponse(
                group.name,
                money(group.current),
                share,
                money(group.previous),
                money(group.current.subtract(group.previous))
        );
    }

    private static boolean isBigger(Clean candidate, Clean best) {
        int byAmount = candidate.amount().compareTo(best.amount());

        if (byAmount != 0) {
            return byAmount > 0;
        }

        int byDate = candidate.date().compareTo(best.date());

        if (byDate != 0) {
            return byDate > 0;
        }

        return byName(candidate.merchant(), best.merchant()) < 0;
    }

    private static boolean isBiggerChange(Group candidate, Group best) {
        BigDecimal candidateChange = candidate.current.subtract(candidate.previous).abs();
        BigDecimal bestChange = best.current.subtract(best.previous).abs();

        int byChange = candidateChange.compareTo(bestChange);

        if (byChange != 0) {
            return byChange > 0;
        }

        int byAmount = candidate.current.compareTo(best.current);

        if (byAmount != 0) {
            return byAmount > 0;
        }

        return byName(candidate.name, best.name) < 0;
    }

    private static boolean isBetterMerchant(Group candidate, Group best) {
        int byAmount = candidate.current.compareTo(best.current);

        if (byAmount != 0) {
            return byAmount > 0;
        }

        if (candidate.visits != best.visits) {
            return candidate.visits > best.visits;
        }

        return byName(candidate.name, best.name) < 0;
    }

    private static String label(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.replaceAll("[\\p{Z}\\s]+", " ").trim();

        if (RecurringExpenseDetector.normalize(trimmed).isEmpty()) {
            return fallback;
        }

        return trimmed;
    }

    private static int byName(String first, String second) {
        int byKey = key(first).compareTo(key(second));

        return byKey != 0 ? byKey : first.compareTo(second);
    }

    private static String key(String name) {
        return RecurringExpenseDetector.normalize(name);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
