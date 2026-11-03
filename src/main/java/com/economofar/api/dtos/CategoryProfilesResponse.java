package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * One profile per category of the saved tickets dated from months months back from today up to
 * today (from and to are ISO dates).
 *
 * ticketCount and totalAmount cover every ticket of the period. categoryCount is the number of
 * categories and categories holds the 100 with most spending, the first one being the one with the
 * most spending.
 */
public record CategoryProfilesResponse(
        int months,
        String from,
        String to,
        int ticketCount,
        BigDecimal totalAmount,
        int categoryCount,
        List<CategoryProfileResponse> categories
) {
}
