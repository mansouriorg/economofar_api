package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One day of the weekly summary. date is ISO (yyyy-MM-dd).
 */
public record DigestDayResponse(
        String date,
        BigDecimal amount,
        int ticketCount
) {
}
