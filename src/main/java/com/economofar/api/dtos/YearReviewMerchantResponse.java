package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One shop of the year review: the amount spent there and the number of tickets.
 */
public record YearReviewMerchantResponse(
        String name,
        BigDecimal total,
        int tickets
) {
}
