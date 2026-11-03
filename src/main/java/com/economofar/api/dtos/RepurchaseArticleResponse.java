package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * An article that is bought at a regular rhythm. key is the way the article is recognised (the detail takes
 * it back), name the spelling written most often. cycleDays is the usual number of days between two
 * purchase days (the middle of the gaps), minIntervalDays and maxIntervalDays the shortest and the longest
 * gap. purchaseDays are the different days it was bought in the period, totalQuantity the quantity bought,
 * averageQuantity the quantity bought on an average purchase day (one decimal).
 *
 * spent is the amount of its lines that have a price, averageSpend the spending of an average purchase
 * day, monthlyEstimate what the article costs in 30 days at its rhythm (spent x 30 / (purchaseDays x cycleDays)).
 * firstDate, lastDate and nextDate are "yyyy-MM-dd": nextDate is lastDate plus cycleDays. daysSinceLast
 * is the number of days from lastDate to today, daysUntilNext the days left before nextDate (negative
 * when it is past). usualShop is the shop where it is bought on most days, null when no ticket names one.
 *
 * status is "DUE" (the cycle has passed), "SOON" (three quarters of the cycle have passed), "OK" or
 * "LAPSED" (more than three cycles without a purchase: the article is probably not bought any more).
 */
public record RepurchaseArticleResponse(
        String key,
        String name,
        String status,
        int cycleDays,
        int minIntervalDays,
        int maxIntervalDays,
        int purchaseDays,
        long totalQuantity,
        BigDecimal averageQuantity,
        BigDecimal spent,
        BigDecimal averageSpend,
        BigDecimal monthlyEstimate,
        String firstDate,
        String lastDate,
        String nextDate,
        int daysSinceLast,
        int daysUntilNext,
        String usualShop
) {
}
