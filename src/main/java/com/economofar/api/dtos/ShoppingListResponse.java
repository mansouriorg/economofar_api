package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole shopping list: items still to buy first, then the ones already checked.
 *
 * estimatedTotal covers the items not checked yet that have a known price.
 * potentialSavings is what would be saved by buying them in the cheaper shop found in the tickets.
 */
public record ShoppingListResponse(
        List<ShoppingListItemResponse> items,
        BigDecimal estimatedTotal,
        BigDecimal potentialSavings,
        int pricedCount,
        int unpricedCount,
        int checkedCount,
        int totalCount,
        List<ShoppingSuggestionResponse> suggestions
) {
}
