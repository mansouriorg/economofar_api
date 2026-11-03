package com.economofar.api.services;

import com.economofar.api.dtos.MonthPartRhythmResponse;
import com.economofar.api.dtos.SpendingRhythmResponse;
import com.economofar.api.dtos.WeekdayRhythmResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows when the user spends: which days of the week and which parts of the month cost the most,
 * and how many days in a row nothing was bought.
 *
 * The period is {@code weeks} whole weeks ending today (4 to 52, today included). Only the days
 * since the first purchase of the period are used, so that the days before the user started to
 * record purchases do not count as days without purchase and do not lower the averages. The
 * averages divide by the real number of days, so a day of the week that occurs once more than the
 * others is not favoured, and they count every day, the ones without purchase too.
 *
 * With fewer than 14 days of history the week and the month cannot be compared: the busiest and
 * the quietest day of the week are not given.
 *
 * Only purchases with an amount above zero, dated inside the period, are used (the service counts
 * a purchase dated tomorrow as today). The answer never depends on the order of the purchases.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class SpendingRhythmCalculator {

    public record Purchase(BigDecimal amount, LocalDate date) {
    }

    public static final int MIN_WEEKS = 4;
    public static final int MAX_WEEKS = 52;

    static final int MIN_PROFILE_DAYS = 14;

    static final String[] PART_KEYS = {"start", "middle", "end"};

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SpendingRhythmCalculator() {
    }

    public static int clampWeeks(int weeks) {
        return Math.min(Math.max(weeks, MIN_WEEKS), MAX_WEEKS);
    }

    /** The first day of the period: {@code weeks} whole weeks, today included. */
    public static LocalDate windowStart(LocalDate today, int weeks) {
        return today.minusDays(clampWeeks(weeks) * 7L - 1L);
    }

    static int partIndex(LocalDate date) {
        int day = date.getDayOfMonth();

        if (day <= 10) {
            return 0;
        }

        if (day <= 20) {
            return 1;
        }

        return 2;
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

    private static BigDecimal average(BigDecimal total, int days) {
        if (days <= 0) {
            return money(BigDecimal.ZERO);
        }

        return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }

        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static boolean isHigher(BigDecimal totalA, int daysA, BigDecimal totalB, int daysB) {
        return totalA.multiply(BigDecimal.valueOf(daysB)).compareTo(totalB.multiply(BigDecimal.valueOf(daysA))) > 0;
    }

    public static SpendingRhythmResponse analyse(List<Purchase> purchases, LocalDate today, int requestedWeeks) {
        int weeks = clampWeeks(requestedWeeks);
        int windowDays = weeks * 7;
        LocalDate from = windowStart(today, weeks);

        BigDecimal[] daySums = zeros(windowDays);
        int[] dayCounts = new int[windowDays];

        BigDecimal total = BigDecimal.ZERO;
        int purchaseCount = 0;

        if (purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase == null || purchase.amount() == null || purchase.date() == null) {
                    continue;
                }

                if (purchase.amount().signum() <= 0) {
                    continue;
                }

                if (purchase.date().isBefore(from) || purchase.date().isAfter(today)) {
                    continue;
                }

                int index = (int) ChronoUnit.DAYS.between(from, purchase.date());

                daySums[index] = daySums[index].add(purchase.amount());
                dayCounts[index]++;
                total = total.add(purchase.amount());
                purchaseCount++;
            }
        }

        int firstIndex = -1;

        for (int i = 0; i < windowDays; i++) {
            if (dayCounts[i] > 0) {
                firstIndex = i;
                break;
            }
        }

        int trackedDays = firstIndex < 0 ? 0 : windowDays - firstIndex;
        boolean enoughHistory = trackedDays >= MIN_PROFILE_DAYS;

        BigDecimal[] weekdayTotals = zeros(7);
        int[] weekdayDays = new int[7];
        int[] weekdayActive = new int[7];
        int[] weekdayPurchases = new int[7];

        BigDecimal[] partTotals = zeros(3);
        int[] partDays = new int[3];

        int activeDays = 0;
        int run = 0;
        int longest = 0;
        int biggestIndex = -1;

        if (firstIndex >= 0) {
            for (int i = firstIndex; i < windowDays; i++) {
                LocalDate date = from.plusDays(i);
                int dow = date.getDayOfWeek().getValue() - 1;
                int part = partIndex(date);

                weekdayTotals[dow] = weekdayTotals[dow].add(daySums[i]);
                weekdayDays[dow]++;
                weekdayPurchases[dow] += dayCounts[i];
                partTotals[part] = partTotals[part].add(daySums[i]);
                partDays[part]++;

                if (dayCounts[i] > 0) {
                    activeDays++;
                    weekdayActive[dow]++;
                    run = 0;

                    if (biggestIndex < 0 || daySums[i].compareTo(daySums[biggestIndex]) >= 0) {
                        biggestIndex = i;
                    }
                } else {
                    run++;
                    longest = Math.max(longest, run);
                }
            }
        }

        Integer busiest = null;
        Integer quietest = null;
        BigDecimal peak = BigDecimal.ZERO.setScale(1);

        if (enoughHistory && total.signum() > 0) {
            int best = 0;
            int worst = 0;

            for (int d = 1; d < 7; d++) {
                if (isHigher(weekdayTotals[d], weekdayDays[d], weekdayTotals[best], weekdayDays[best])) {
                    best = d;
                }

                if (isHigher(weekdayTotals[worst], weekdayDays[worst], weekdayTotals[d], weekdayDays[d])) {
                    worst = d;
                }
            }

            busiest = best + 1;
            quietest = worst + 1;

            peak = weekdayTotals[best]
                    .multiply(BigDecimal.valueOf(trackedDays))
                    .divide(total.multiply(BigDecimal.valueOf(weekdayDays[best])), 1, RoundingMode.HALF_UP);
        }

        List<WeekdayRhythmResponse> weekdays = new ArrayList<>();

        for (int d = 0; d < 7; d++) {
            weekdays.add(new WeekdayRhythmResponse(
                    d + 1,
                    money(weekdayTotals[d]),
                    average(weekdayTotals[d], weekdayDays[d]),
                    share(weekdayTotals[d], total),
                    weekdayPurchases[d],
                    weekdayActive[d],
                    weekdayDays[d]
            ));
        }

        List<MonthPartRhythmResponse> monthParts = new ArrayList<>();

        for (int p = 0; p < 3; p++) {
            monthParts.add(new MonthPartRhythmResponse(
                    PART_KEYS[p],
                    partDays[p],
                    money(partTotals[p]),
                    average(partTotals[p], partDays[p]),
                    share(partTotals[p], total)
            ));
        }

        BigDecimal weekendTotal = weekdayTotals[5].add(weekdayTotals[6]);
        int weekendDays = weekdayDays[5] + weekdayDays[6];

        BigDecimal workTotal = BigDecimal.ZERO;
        int workDays = 0;

        for (int d = 0; d < 5; d++) {
            workTotal = workTotal.add(weekdayTotals[d]);
            workDays += weekdayDays[d];
        }

        String trackedFrom = firstIndex < 0 ? null : from.plusDays(firstIndex).toString();
        String biggestDate = biggestIndex < 0 ? null : from.plusDays(biggestIndex).toString();
        BigDecimal biggestAmount = biggestIndex < 0 ? null : money(daySums[biggestIndex]);

        return new SpendingRhythmResponse(
                weeks,
                from.toString(),
                today.toString(),
                trackedFrom,
                trackedDays,
                enoughHistory,
                money(total),
                purchaseCount,
                activeDays,
                trackedDays - activeDays,
                average(total, trackedDays),
                weekdays,
                busiest,
                quietest,
                peak,
                money(weekendTotal),
                share(weekendTotal, total),
                average(weekendTotal, weekendDays),
                average(workTotal, workDays),
                monthParts,
                biggestDate,
                biggestAmount,
                run,
                longest
        );
    }
}
