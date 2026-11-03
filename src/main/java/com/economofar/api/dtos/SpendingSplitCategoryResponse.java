package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A category that has tickets in the month. key is the name without the spaces around it and without
 * capitals, the way the rules are kept; name is the way the tickets write it, "Sans cat\u00e9gorie" for
 * the tickets that have none (key is then empty). group is where the category counts now: "NEED",
 * "WANT", "UNSORTED" (not classified yet) or "EXCLUDED" (left out of the calculation). defaultGroup is
 * where the app proposes to put it ("NEED", "WANT" or "UNSORTED"). custom tells that a rule of the
 * person decides the group, editable that a rule can be set (not for the tickets without category).
 * amount is the sum of the totals of the tickets of the month, a refund being taken off.
 */
public record SpendingSplitCategoryResponse(
        String key,
        String name,
        String group,
        String defaultGroup,
        boolean custom,
        boolean editable,
        int ticketCount,
        BigDecimal amount
) {
}
