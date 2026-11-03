package com.economofar.api.dtos;

import java.math.BigDecimal;

/** The small purchases of one month: how many there were and how much they came to. */
public record SmallMonthResponse(
        int count,
        BigDecimal total
) {
}
