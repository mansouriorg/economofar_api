package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * The biggest single receipt of the last 7 days. date is ISO (yyyy-MM-dd).
 */
public record DigestPurchaseResponse(
        String merchant,
        BigDecimal amount,
        String date
) {
}
