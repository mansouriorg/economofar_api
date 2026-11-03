package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The saved tickets without category (no category or "Autre") of the last months, and the
 * categories that can be suggested for them.
 *
 * from and to are the first and the last day of the period ("2026-04-18", "2026-10-18").
 * uncategorizedCount and uncategorizedAmount are about all the tickets of the period without
 * category; suggestedCount and suggestedAmount about those that a suggestion covers;
 * unresolvedCount and unresolvedAmount about the others (unknown merchant, or no reliable
 * category for the merchant). Amounts count only the amounts above zero. groups has the
 * merchants with a suggestion, the biggest amount first (50 at most); groupCount counts all of
 * them.
 */
public record CategorySuggestionsResponse(
        int months,
        String from,
        String to,
        int uncategorizedCount,
        BigDecimal uncategorizedAmount,
        int suggestedCount,
        BigDecimal suggestedAmount,
        int unresolvedCount,
        BigDecimal unresolvedAmount,
        int groupCount,
        List<CategorySuggestionGroupResponse> groups
) {
}
