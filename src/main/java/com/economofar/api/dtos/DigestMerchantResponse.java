package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * The shop where most was spent over the last 7 days. visits is the number of receipts.
 */
public record DigestMerchantResponse(
        String name,
        BigDecimal amount,
        int visits
) {
}
