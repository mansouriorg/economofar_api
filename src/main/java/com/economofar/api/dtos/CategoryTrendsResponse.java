package com.economofar.api.dtos;

import java.util.List;

/**
 * The spending of the last finished months, category by category.
 *
 * monthKeys are the months of the window, the oldest first ("2026-04"); from and to are the first
 * and the last of them. trackedMonths counts the months from the first one with a ticket.
 * compareMonths is the number of recent months (and of earlier months) that the trends compare.
 * overall is the trend of the whole spending. categories are the biggest ones, the smallest ones
 * being merged into one line; categoryCount is the number of categories before merging.
 */
public record CategoryTrendsResponse(
        String from,
        String to,
        int months,
        List<String> monthKeys,
        int trackedMonths,
        int compareMonths,
        CategoryTrendResponse overall,
        int categoryCount,
        List<CategoryTrendResponse> categories
) {
}
