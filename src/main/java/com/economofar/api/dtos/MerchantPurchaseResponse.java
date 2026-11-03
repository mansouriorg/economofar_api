package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One receipt of a shop: date (yyyy-MM-dd), amount and category.
 */
public record MerchantPurchaseResponse(
        String date,
        BigDecimal amount,
        String category
) {
}
