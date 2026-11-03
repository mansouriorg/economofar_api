package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * What the user can still spend today without leaving the monthly budget.
 *
 * date is the day this answer is about (yyyy-MM-dd). status is "none" (no monthly budget),
 * "good", "tight" (80 percent or more of the day already used), "over" (more than the day's
 * share) or "exhausted" (nothing left for the month once the regular payments to come are kept).
 *
 * spentToday does not include the regular payments (rent, subscriptions...) recorded today, they
 * are in recurringToday, because they were planned and must not eat the daily share.
 * monthRemaining is negative when the budget is already passed. upcomingRecurring is the total of
 * the regular payments still expected until the end of the month, today included.
 * monthlyBudget, dailyBudget, remainingToday, tomorrowBudget and monthRemaining are null when
 * they do not apply (no budget; tomorrowBudget is also null on the last day of the month).
 */
public record DailyBudgetResponse(
        String date,
        String status,
        int daysLeft,
        BigDecimal monthlyBudget,
        BigDecimal dailyBudget,
        BigDecimal spentToday,
        BigDecimal recurringToday,
        BigDecimal remainingToday,
        BigDecimal tomorrowBudget,
        BigDecimal monthRemaining,
        BigDecimal upcomingRecurring
) {
}
