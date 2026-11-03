package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The business tickets that share one expense report, one project or one client.
 *
 * key is the name without case, accents and punctuation: it does not change when the name is
 * written in another way, and name is the way it is written most often. amount adds the amounts
 * above zero and vat the VAT amounts above zero. recent holds 10 tickets at most, those of the latest
 * days: the most recent day first, and the biggest amount first within a day.
 */
public record BusinessGroupResponse(
        String key,
        String name,
        int ticketCount,
        BigDecimal amount,
        BigDecimal vat,
        List<BusinessTicketResponse> recent
) {
}
