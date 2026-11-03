package com.economofar.api.services;

import com.economofar.api.dtos.DailyBudgetResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits what is left of the monthly budget over the days that are left, today included.
 *
 * pool = monthly budget - spent before today - regular payments recorded today
 *        - regular payments still expected until the end of the month
 * daily budget = pool / days left (never below zero)
 * left today = daily budget - spending of today (regular payments not counted)
 *
 * The regular payments are taken out of the pool, not out of the day, so the daily budget stays
 * the same whether a payment such as the rent has been recorded yet or not, as long as it is
 * recorded for its usual amount (a different amount moves the daily budget by the difference
 * spread over the days left, because that money really went out). A purchase at the
 * shop of a regular payment counts as that payment up to 1.5 times its usual amount (bills vary a
 * little); what goes beyond that is ordinary spending of the day, so a big shopping at a shop that
 * is also a weekly habit is not hidden. A payment that is more than a week late is not in the
 * regular payments to come (SpendingForecaster ignores it), so recording it lowers the daily
 * budget: it is real money going out that was not set aside.
 *
 * When the day is over, what was not spent is shared again between the remaining days
 * (tomorrowBudget). That number is the same as the daily allowance of the month forecast, because
 * both use SpendingForecaster for the regular payments to come and the same rules for the
 * spending that counts.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class DailyBudgetCalculator {

    public static final String STATUS_NONE = "none";
    public static final String STATUS_GOOD = "good";
    public static final String STATUS_TIGHT = "tight";
    public static final String STATUS_OVER = "over";
    public static final String STATUS_EXHAUSTED = "exhausted";

    static final BigDecimal TIGHT_SHARE = new BigDecimal("0.80");
    static final BigDecimal PLANNED_TOLERANCE = new BigDecimal("1.50");

    private DailyBudgetCalculator() {
    }

    public static DailyBudgetResponse compute(
            List<SpendingForecaster.Spend> spends,
            List<SpendingForecaster.Recurring> recurring,
            BigDecimal monthlyBudget,
            LocalDate today
    ) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        int daysLeft = today.lengthOfMonth() - today.getDayOfMonth() + 1;

        List<List<String>> itemKeys = new ArrayList<>();

        if (recurring != null) {
            for (SpendingForecaster.Recurring item : recurring) {
                itemKeys.add(item == null ? List.of() : SpendingForecaster.recurringKeys(List.of(item)));
            }
        }

        boolean[] matchedItem = new boolean[itemKeys.size()];

        BigDecimal spentBefore = BigDecimal.ZERO;
        BigDecimal variableToday = BigDecimal.ZERO;
        BigDecimal regularToday = BigDecimal.ZERO;

        if (spends != null) {
            for (SpendingForecaster.Spend spend : spends) {
                if (spend == null
                        || spend.date() == null
                        || spend.amount() == null
                        || spend.amount().signum() <= 0
                        || spend.date().isBefore(monthStart)
                        || spend.date().isAfter(today)) {
                    continue;
                }

                if (spend.date().isBefore(today)) {
                    spentBefore = spentBefore.add(spend.amount());
                    continue;
                }

                boolean regular = false;

                for (int i = 0; i < itemKeys.size(); i++) {
                    if (SpendingForecaster.isRecurring(spend.merchant(), itemKeys.get(i))) {
                        matchedItem[i] = true;
                        regular = true;
                    }
                }

                if (regular) {
                    regularToday = regularToday.add(spend.amount());
                } else {
                    variableToday = variableToday.add(spend.amount());
                }
            }
        }

        BigDecimal planned = BigDecimal.ZERO;

        if (recurring != null) {
            int index = 0;

            for (SpendingForecaster.Recurring item : recurring) {
                if (matchedItem[index] && item.amount() != null && item.amount().signum() > 0) {
                    planned = planned.add(item.amount().multiply(PLANNED_TOLERANCE));
                }

                index++;
            }
        }

        BigDecimal recurringToday = regularToday.min(planned.setScale(2, RoundingMode.DOWN));
        variableToday = variableToday.add(regularToday.subtract(recurringToday));

        BigDecimal upcoming = SpendingForecaster
                .forecast(spends, recurring, monthlyBudget, today)
                .upcomingRecurring();

        BigDecimal spentToday = variableToday.setScale(2, RoundingMode.HALF_UP);
        BigDecimal recurringTodayShown = recurringToday.setScale(2, RoundingMode.HALF_UP);

        if (monthlyBudget == null || monthlyBudget.signum() <= 0) {
            return new DailyBudgetResponse(
                    today.toString(),
                    STATUS_NONE,
                    daysLeft,
                    null,
                    null,
                    spentToday,
                    recurringTodayShown,
                    null,
                    null,
                    null,
                    upcoming
            );
        }

        BigDecimal budget = monthlyBudget.setScale(2, RoundingMode.HALF_UP);

        BigDecimal pool = budget
                .subtract(spentBefore)
                .subtract(recurringToday)
                .subtract(upcoming);

        BigDecimal dailyBudget = pool.signum() > 0
                ? pool.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        BigDecimal remainingToday = dailyBudget.subtract(spentToday);

        BigDecimal monthLeft = pool.subtract(variableToday);
        BigDecimal monthRemaining = monthLeft.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tomorrow = null;

        if (daysLeft > 1) {
            tomorrow = monthLeft.signum() > 0
                    ? monthLeft.divide(BigDecimal.valueOf(daysLeft - 1), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
        }

        String status;

        if (dailyBudget.signum() <= 0) {
            status = STATUS_EXHAUSTED;
        } else if (remainingToday.signum() < 0) {
            status = STATUS_OVER;
        } else if (spentToday.compareTo(dailyBudget.multiply(TIGHT_SHARE)) >= 0) {
            status = STATUS_TIGHT;
        } else {
            status = STATUS_GOOD;
        }

        return new DailyBudgetResponse(
                today.toString(),
                status,
                daysLeft,
                budget,
                dailyBudget,
                spentToday,
                recurringTodayShown,
                remainingToday,
                tomorrow,
                monthRemaining,
                upcoming
        );
    }
}
