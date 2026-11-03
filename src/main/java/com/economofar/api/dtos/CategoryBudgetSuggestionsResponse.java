package com.economofar.api.dtos;

import java.util.List;

/**
 * Suggested category limits. basedOnMonths is the number of finished months with some spending
 * that were used (0 to 3); there is no suggestion when it is 0.
 */
public record CategoryBudgetSuggestionsResponse(
        int basedOnMonths,
        List<CategoryBudgetSuggestionResponse> suggestions
) {
}
