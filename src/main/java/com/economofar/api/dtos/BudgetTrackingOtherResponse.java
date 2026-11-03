package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A category that has no budget but has spending in the month. key is the name of the category
 * without the spaces around it and without capitals, empty for the tickets without category, and
 * name is the spelling used most often ("Sans cat\u00e9gorie" for those). amount is above zero.
 */
public record BudgetTrackingOtherResponse(
        String key,
        String name,
        int ticketCount,
        BigDecimal amount
) {
}
