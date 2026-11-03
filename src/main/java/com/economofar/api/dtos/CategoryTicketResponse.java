package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ticket of a category. date is an ISO date ("2026-10-17"), merchant is the name of its merchant
 * as the profile shows it ("Enseigne inconnue" when it is not known) and amount counts only an amount
 * above zero.
 */
public record CategoryTicketResponse(
        UUID id,
        String merchant,
        String date,
        BigDecimal amount
) {
}
