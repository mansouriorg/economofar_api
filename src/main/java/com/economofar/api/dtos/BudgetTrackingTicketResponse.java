package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ticket of a category in the month. date is an ISO date ("2026-10-17"), merchant is the name of
 * its merchant as written ("Enseigne inconnue" when it is not known) and amount is the total of the
 * ticket as it is, or null when the ticket has no total.
 */
public record BudgetTrackingTicketResponse(
        UUID id,
        String merchant,
        String date,
        BigDecimal amount
) {
}
