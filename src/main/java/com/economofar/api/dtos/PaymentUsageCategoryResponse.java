package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * What the tickets of a payment method came to in one category. name is "Autre" for the tickets
 * without category.
 */
public record PaymentUsageCategoryResponse(
        String name,
        int ticketCount,
        BigDecimal amount
) {
}
