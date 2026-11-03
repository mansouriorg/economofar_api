package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One price paid for an item on one day. date is an ISO string (yyyy-MM-dd).
 * merchantName can be null when the shop is not known.
 */
public record PricePointResponse(
        String date,
        BigDecimal price,
        String merchantName
) {
}
