package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * Spending of one category over the last 7 days compared with the 7 days before.
 *
 * share is the percentage of the total of the last 7 days (one decimal). change is
 * amount - previousAmount and is negative when the category went down.
 */
public record DigestCategoryResponse(
        String name,
        BigDecimal amount,
        BigDecimal share,
        BigDecimal previousAmount,
        BigDecimal change
) {
}
