package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * What was spent over the last 7 days (today included) compared with the 7 days before.
 *
 * from and to are ISO dates (yyyy-MM-dd) of the last 7 days. trend is "up", "down", "stable"
 * (less than 5 percent of difference), "new" (nothing was spent in the 7 days before, so there is
 * nothing to compare with) or "empty" (nothing was spent in the 14 days). changePercent is null
 * unless the 7 days before had spending.
 *
 * days has exactly 7 entries, the oldest first. categories has at most 5 entries, the biggest
 * first. topMerchant, biggestPurchase and biggestChange are null when they do not apply
 * (biggestChange needs spending in the 7 days before).
 */
public record WeeklyDigestResponse(
        String from,
        String to,
        BigDecimal total,
        BigDecimal previousTotal,
        int ticketCount,
        int previousTicketCount,
        BigDecimal averagePerDay,
        BigDecimal changePercent,
        String trend,
        int noSpendDays,
        List<DigestDayResponse> days,
        List<DigestCategoryResponse> categories,
        DigestMerchantResponse topMerchant,
        DigestPurchaseResponse biggestPurchase,
        DigestCategoryResponse biggestChange
) {
}
