package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One line of the ranking of the shops. share is the percentage of all the spending of the period,
 * lastVisit is the date (yyyy-MM-dd) of the last receipt and topCategory is the category where
 * most was spent in that shop.
 */
public record MerchantSummaryResponse(
        String name,
        BigDecimal total,
        BigDecimal share,
        int tickets,
        BigDecimal averageTicket,
        String lastVisit,
        int daysSinceLastVisit,
        String topCategory
) {
}
