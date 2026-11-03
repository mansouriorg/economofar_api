package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The biggest ticket of the year. date is yyyy-MM-dd.
 */
public record YearReviewTicketResponse(
        UUID id,
        String merchant,
        String category,
        String date,
        BigDecimal amount
) {
}
