package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The small purchases (an amount up to the threshold, in euros) of the last finished months.
 *
 * monthKeys are the months of the window, the oldest first ("2026-04"); from and to are the first
 * and the last of them. trackedMonths counts the months from the first one with a ticket.
 * purchaseCount and totalAmount are about all the purchases of the window, smallCount and
 * smallTotal only about the small ones. monthlyAverage, yearlyEstimate (twelve times the monthly
 * average) and yearlySavingIfQuarterFewer (a quarter of the yearly estimate) are null when no month is
 * tracked. smallAverage and daysPerPurchase are null when there is no small purchase.
 * shareOfSpending and shareOfPurchases are percentages with one decimal, null when there is nothing
 * to compare with. perMonth has one entry per month of the window. merchants and categories are
 * the biggest ones (5 at most); merchantCount and categoryCount count all of them.
 */
public record SmallPurchasesResponse(
        String from,
        String to,
        int months,
        int threshold,
        List<String> monthKeys,
        int trackedMonths,
        int purchaseCount,
        BigDecimal totalAmount,
        int smallCount,
        BigDecimal smallTotal,
        BigDecimal smallAverage,
        BigDecimal monthlyAverage,
        BigDecimal yearlyEstimate,
        BigDecimal yearlySavingIfQuarterFewer,
        BigDecimal shareOfSpending,
        BigDecimal shareOfPurchases,
        BigDecimal daysPerPurchase,
        List<SmallMonthResponse> perMonth,
        int merchantCount,
        List<SmallGroupResponse> merchants,
        int categoryCount,
        List<SmallGroupResponse> categories
) {
}
