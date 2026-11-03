package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * What one payment method came to in one calendar month. month is "2026-10". A month without any
 * ticket is listed with 0 tickets and an amount of 0.00.
 */
public record PaymentUsageMonthResponse(
        String month,
        int ticketCount,
        BigDecimal amount
) {
}
