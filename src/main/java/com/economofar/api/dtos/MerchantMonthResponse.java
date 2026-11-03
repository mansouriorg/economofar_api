package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * What was spent in a shop during one month (month is yyyy-MM). tickets is the number of receipts.
 */
public record MerchantMonthResponse(
        String month,
        BigDecimal total,
        int tickets
) {
}
