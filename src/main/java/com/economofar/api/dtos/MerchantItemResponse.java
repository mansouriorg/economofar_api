package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * An article bought in a shop: how many were bought and what they cost in all. The articles are
 * listed by the money they cost, the biggest first.
 */
public record MerchantItemResponse(
        String name,
        int quantity,
        BigDecimal total
) {
}
