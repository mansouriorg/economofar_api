package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One category of the month comparison. current and previous are the amounts spent in the
 * compared days of the two months, delta is current minus previous and deltaPercent is delta as a
 * percentage of previous (one decimal), null when nothing was spent in that category in the
 * previous month. share is the percentage of the month's spending (one decimal).
 */
public record MonthCompareCategoryResponse(
        String name,
        BigDecimal current,
        BigDecimal previous,
        BigDecimal delta,
        BigDecimal deltaPercent,
        BigDecimal share
) {
}
