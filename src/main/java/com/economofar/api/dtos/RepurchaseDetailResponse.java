package com.economofar.api.dtos;

import java.util.List;

/**
 * One article with a regular rhythm. intervals are the days between two purchase days, the oldest first,
 * the latest 30 at most (hiddenIntervalCount is how many older ones are left out). purchases are the lines
 * of the tickets, the newest first, 20 at most (hiddenPurchaseCount is how many more there are). shops are
 * the named shops where it was bought, the one with the most purchase days first, 5 at most.
 */
public record RepurchaseDetailResponse(
        int months,
        String from,
        String to,
        RepurchaseArticleResponse article,
        List<Integer> intervals,
        int hiddenIntervalCount,
        List<RepurchasePurchaseResponse> purchases,
        int hiddenPurchaseCount,
        List<RepurchaseShopResponse> shops
) {
}
