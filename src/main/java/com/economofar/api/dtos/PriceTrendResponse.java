package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * How the price of one item changed across the saved tickets.
 *
 * trend is "up", "down" or "stable". changePercent compares the last price with the first one.
 * cheaperMerchant and cheaperPrice are filled only when another shop recently sold the same
 * item at least 5 percent cheaper than the last purchase.
 */
public record PriceTrendResponse(
        String name,
        int purchaseCount,
        String firstDate,
        String lastDate,
        BigDecimal firstPrice,
        BigDecimal previousPrice,
        BigDecimal lastPrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal averagePrice,
        BigDecimal changePercent,
        String trend,
        String lastMerchant,
        String cheaperMerchant,
        BigDecimal cheaperPrice,
        List<PricePointResponse> history
) {
}
