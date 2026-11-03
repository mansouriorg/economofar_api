package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A category of the receipts of one shop. share is the percentage of the spending in that shop.
 */
public record MerchantCategoryResponse(
        String name,
        BigDecimal total,
        BigDecimal share
) {
}
