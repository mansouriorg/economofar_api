package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ticket that has a warranty date.
 *
 * purchaseDate and warrantyDate are ISO dates ("2026-03-12"); purchaseDate is null when the ticket
 * has none. daysLeft is the number of days from today to the warranty date: 0 on the last day, and
 * negative once the warranty is over (-1 the day after). amount counts only an amount above zero.
 * note is the note of the ticket on one line, cut after 120 characters, or null when it has none.
 */
public record WarrantyOverviewItemResponse(
        UUID id,
        String merchant,
        String note,
        BigDecimal amount,
        String purchaseDate,
        String warrantyDate,
        int daysLeft
) {
}
