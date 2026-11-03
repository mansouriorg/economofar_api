package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The tickets of one merchant that have no category, and the category suggested for all of them.
 *
 * category is the category that the merchant already has most often. supportCount is the number
 * of tickets of the merchant that are already in that category and categorizedCount the number of
 * tickets of the merchant that have a category at all; confidence is supportCount as a percentage
 * of categorizedCount, with one decimal. ticketCount and amount are about all the tickets to fix
 * (amount counts only the amounts above zero); ticketIds lists the first 200 of them, the most
 * recent first.
 */
public record CategorySuggestionGroupResponse(
        String merchant,
        String category,
        BigDecimal confidence,
        int supportCount,
        int categorizedCount,
        int ticketCount,
        BigDecimal amount,
        List<UUID> ticketIds
) {
}
