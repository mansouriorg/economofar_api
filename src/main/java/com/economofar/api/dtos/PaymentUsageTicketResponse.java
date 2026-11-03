package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ticket paid with a payment method. date is an ISO date ("2026-10-17"), merchant is the name
 * of its merchant as written ("Enseigne inconnue" when it is not known) and amount counts only an
 * amount above zero.
 */
public record PaymentUsageTicketResponse(
        UUID id,
        String merchant,
        String date,
        BigDecimal amount
) {
}
