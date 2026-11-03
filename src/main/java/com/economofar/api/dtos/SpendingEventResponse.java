package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * An event and where its spending stands. spent only counts the saved tickets of the event; remaining and
 * usedPercent need a budget. state is "NO_BUDGET", "OK", "NEAR" (80 percent of the budget or more, not
 * over it) or "OVER". timing is "UPCOMING", "ONGOING", "DONE" or "UNDATED" (no dates at all).
 *
 * dayCount is the length of the event (both dates), elapsedDays the days since its start up to today (or
 * to its end), daysLeft the days from today (or from its start when it has not begun) to its end, today
 * included. dailyAllowance is what is left of the budget divided by daysLeft. projected is the total at
 * the pace of the days gone by, only for an ongoing event of known length with at least 3 days gone by
 * and a quarter of its length; projectedOver tells that it is above the budget.
 */
public record SpendingEventResponse(
        long id,
        String name,
        String icon,
        BigDecimal budget,
        String startDate,
        String endDate,
        boolean archived,
        BigDecimal spent,
        int ticketCount,
        int purchaseDays,
        String firstDate,
        String lastDate,
        BigDecimal remaining,
        BigDecimal usedPercent,
        String state,
        String timing,
        Integer dayCount,
        Integer elapsedDays,
        Integer daysLeft,
        BigDecimal dailyAllowance,
        BigDecimal averagePerDay,
        BigDecimal projected,
        boolean projectedOver
) {
}
