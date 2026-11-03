package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estimate of the total spent by the end of the current month.
 *
 * month is "yyyy-MM". dailyPace is the usual daily spending without the regular payments.
 * budgetStatus is "none", "onTrack", "atRisk", "projectedOver" or "exceeded".
 * confidence is "low", "medium" or "high". exceedDate is an ISO date or null.
 * monthlyBudget, projectedOverBudget, dailyAllowance, previousMonthTotal and exceedDate
 * are null when they do not apply.
 */
public record SpendingForecastResponse(
        String month,
        int dayOfMonth,
        int daysInMonth,
        int daysRemaining,
        BigDecimal spentSoFar,
        BigDecimal upcomingRecurring,
        BigDecimal dailyPace,
        BigDecimal projectedTotal,
        BigDecimal projectedLow,
        BigDecimal projectedHigh,
        BigDecimal previousMonthTotal,
        BigDecimal monthlyBudget,
        String budgetStatus,
        BigDecimal projectedOverBudget,
        BigDecimal dailyAllowance,
        String exceedDate,
        String confidence,
        List<ForecastUpcomingResponse> upcoming
) {
}
