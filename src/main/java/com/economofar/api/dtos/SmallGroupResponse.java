package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A merchant or a category among the small purchases: how many there were, how much they came to
 * and the share of all the small purchases that this is, in percent with one decimal.
 */
public record SmallGroupResponse(
        String name,
        int count,
        BigDecimal total,
        BigDecimal share
) {
}
