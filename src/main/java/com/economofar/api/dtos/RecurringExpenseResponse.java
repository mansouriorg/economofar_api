package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One expense that comes back regularly (subscription, weekly bakery, monthly gym...).
 * Dates are ISO strings (yyyy-MM-dd). daysUntilNext is negative when the next one is late.
 */
public record RecurringExpenseResponse(
        String merchantName,
        String category,
        BigDecimal typicalAmount,
        int occurrences,
        String frequency,
        int intervalDays,
        String lastDate,
        String nextExpectedDate,
        long daysUntilNext,
        BigDecimal monthlyEstimate
) {
}
