package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * An item the user buys regularly and that is about to be needed again.
 *
 * intervalDays is the usual time between two purchases, daysSinceLast the time since the last one.
 * due is true when the usual interval is (almost) over.
 */
public record ShoppingSuggestionResponse(
        String name,
        int purchaseCount,
        int intervalDays,
        int daysSinceLast,
        boolean due,
        BigDecimal lastPrice,
        String lastMerchant
) {
}
