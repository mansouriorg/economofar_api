package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything about one shop over the last months (the current month included).
 *
 * share is the percentage of all the spending of the period. averageDaysBetween is the average
 * number of days between two days with a receipt (null with fewer than two such days).
 * tickets is the number of receipts. usualWeekday is the day of the week (1 = Monday ... 7 =
 * Sunday) on which the shop is usually visited: at least three different days fall on it, no other
 * day of the week has as many, and it covers at least two fifths of the days with a receipt (null
 * otherwise). monthToDate is what was spent in the current month so far and previousSamePeriod
 * what was spent during the first days of the month before (as many days as have passed this month,
 * at most the length of that month), so the two can be compared fairly; changePercent is null when
 * nothing was spent in that shop during those days. months always lists every month of the period,
 * even the empty ones. recent, categories and items list at most five entries each, so the shares
 * of the categories do not always add up to 100.
 */
public record MerchantDetailResponse(
        String name,
        int periodMonths,
        String from,
        String to,
        BigDecimal total,
        BigDecimal share,
        int tickets,
        BigDecimal averageTicket,
        String firstVisit,
        String lastVisit,
        int daysSinceLastVisit,
        BigDecimal averageDaysBetween,
        Integer usualWeekday,
        BigDecimal monthToDate,
        BigDecimal previousSamePeriod,
        BigDecimal changePercent,
        MerchantPurchaseResponse biggest,
        List<MerchantPurchaseResponse> recent,
        List<MerchantMonthResponse> months,
        List<MerchantCategoryResponse> categories,
        List<MerchantItemResponse> items
) {
}
