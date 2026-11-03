package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The saved tickets of the period grouped by payment method.
 *
 * The period goes from the first day of the calendar month that is months - 1 months before the
 * current one up to today. methodCount is the number of different methods and methods holds the 20
 * with most spending, the first one being the one with the most spending. unspecifiedCount and
 * unspecifiedAmount are the tickets that have no payment method, and unspecified holds 10 of them
 * at most, those of the latest days, the most recent day first. ticketCount and totalAmount cover
 * all the tickets, with and without a method.
 */
public record PaymentUsageResponse(
        int months,
        String from,
        String to,
        int ticketCount,
        BigDecimal totalAmount,
        int methodCount,
        List<PaymentUsageMethodResponse> methods,
        int unspecifiedCount,
        BigDecimal unspecifiedAmount,
        List<PaymentUsageTicketResponse> unspecified
) {
}
