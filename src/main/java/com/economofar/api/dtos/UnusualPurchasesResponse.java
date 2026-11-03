package com.economofar.api.dtos;

import java.util.List;

/**
 * The purchases of the last days that look unusual, the most unusual first.
 *
 * from and to are the first and the last day of the period (yyyy-MM-dd). examined is the number
 * of purchases of the period, compared is how many of them could be compared with earlier
 * purchases (enough history in the same shop or the same category), flaggedCount is how many look
 * unusual and purchases lists at most ten of them.
 */
public record UnusualPurchasesResponse(
        int days,
        String from,
        String to,
        int examined,
        int compared,
        int flaggedCount,
        List<UnusualPurchaseResponse> purchases
) {
}
