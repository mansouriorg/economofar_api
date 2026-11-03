package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A saved ticket that deserves a look. amount is null when the ticket has none, date is
 * yyyy-MM-dd and issues lists the problems found, in the fixed order UNKNOWN_MERCHANT,
 * NO_CATEGORY, DUPLICATE, MISSING_AMOUNT, FUTURE_DATE.
 */
public record QualityTicketResponse(
        UUID id,
        String date,
        String merchant,
        String category,
        BigDecimal amount,
        List<String> issues
) {
}
