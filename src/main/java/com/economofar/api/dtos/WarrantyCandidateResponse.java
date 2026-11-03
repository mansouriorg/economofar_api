package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A saved receipt without warranty yet, that the user can attach one to.
 */
public record WarrantyCandidateResponse(
        String ticketId,
        String merchantName,
        String purchaseDate,
        BigDecimal totalAmount,
        String category
) {
}
