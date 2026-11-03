package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One category of the year review. share is the percentage of the year's spending (one decimal).
 */
public record YearReviewCategoryResponse(
        String name,
        BigDecimal total,
        BigDecimal share
) {
}
