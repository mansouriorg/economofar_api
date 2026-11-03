package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A limit that could be set for one category. averageSpent is what the user spent on it per
 * month on average, suggestedAmount is that average plus a small margin, rounded up to a round
 * figure. monthsUsed is the number of finished months in which the category was used.
 */
public record CategoryBudgetSuggestionResponse(
        String category,
        BigDecimal averageSpent,
        BigDecimal suggestedAmount,
        int monthsUsed
) {
}
