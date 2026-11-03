package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The spending of one calendar month split into needs, wants and savings (the 50/30/20 rule).
 *
 * month is "2026-10". currentMonth tells that it is the month of today. previousMonth and nextMonth are
 * the months next to it that can be asked for, null at the end of the range (the current month and the
 * 23 before it). ticketCount is the number of saved tickets of the month, with or without a total.
 *
 * income is the income declared for the month (the same figure as the cash flow), hasIncome tells that
 * it is above zero. spent is the spending that counts, which is what the needs, the wants and the
 * unsorted add up to (never below zero, like the expenses of the cash flow). excluded is the spending of
 * the categories that the person left out, excludedCategoryCount their number.
 *
 * groups holds the four slices in this order: needs, wants, savings, unsorted. categories holds the
 * categories that have tickets in the month: the needs first, then the wants, the unsorted and the
 * excluded, each group by amount, the biggest first; categoryCount is their exact number and
 * categories holds 60 at most.
 */
public record SpendingSplitResponse(
        String month,
        boolean currentMonth,
        String previousMonth,
        String nextMonth,
        int ticketCount,
        BigDecimal income,
        boolean hasIncome,
        BigDecimal spent,
        BigDecimal excluded,
        int excludedCategoryCount,
        List<SpendingSplitGroupResponse> groups,
        int categoryCount,
        List<SpendingSplitCategoryResponse> categories
) {
}
