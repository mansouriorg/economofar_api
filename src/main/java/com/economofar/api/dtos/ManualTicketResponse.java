package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The ticket that was saved from a manual entry.
 */
public record ManualTicketResponse(
        UUID id,
        String merchantName,
        BigDecimal totalAmount,
        String purchaseDate,
        String category,
        String paymentMethod,
        String note
) {
}
