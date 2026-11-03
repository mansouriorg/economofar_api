package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One article that was bought again, or in several shops, over the period. key is the name without
 * capitals, accents or extra spaces, the way the tickets are matched; name is the way the tickets write
 * it most often.
 *
 * Prices are unit prices. firstPrice and lastPrice are the prices of the first and of the latest day it
 * was bought (the average of the day when it was bought several times), change their difference and
 * changePercent the difference in percent of the first price with one decimal; the three are null when
 * the article was bought on a single day. trend is "RISING" (up 2 percent or more), "FALLING" (down 2
 * percent or more), "STEADY", or "NONE" when there is nothing to compare.
 *
 * merchantCount is the number of named shops. cheapestMerchant, cheapestPrice and gapPercent are null when
 * there are fewer than two of them, or when they all have the same average price; gapPercent is the
 * difference between the dearest and the cheapest average price in percent of the cheapest one.
 * potentialSaving is null with fewer than two named shops, otherwise what would have been saved by paying
 * the cheapest average price each time (0.00 when all the shops have the same average price).
 */
public record ItemPriceResponse(
        String key,
        String name,
        int purchaseCount,
        int dayCount,
        long quantity,
        BigDecimal spent,
        BigDecimal averagePrice,
        BigDecimal lowestPrice,
        BigDecimal highestPrice,
        String firstDate,
        String lastDate,
        BigDecimal firstPrice,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal changePercent,
        String trend,
        int merchantCount,
        String cheapestMerchant,
        BigDecimal cheapestPrice,
        BigDecimal gapPercent,
        BigDecimal potentialSaving
) {
}
