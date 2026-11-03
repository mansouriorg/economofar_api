package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The stored budgets of the categories compared with the saved tickets of one calendar month.
 *
 * month is "2026-10". currentMonth tells that it is the month of today. previousMonth and nextMonth
 * are the months next to it that can be asked for, null at the end of the range (the current month
 * and the 23 before it). dayOfMonth is today's day of the month (the last day for a month that has
 * ended), daysInMonth is the length of the month and daysLeft is the days up to the end of the month,
 * today included (0 for a month that has ended).
 *
 * ticketCount and monthSpent cover all the tickets of the month, with or without a budget.
 * budgetTotal is the sum of the budgets, budgetSpent what was spent in these categories (the sum of
 * the lines), budgetRemaining the difference (negative when over), and percentUsed budgetSpent
 * divided by budgetTotal in percent with one decimal, null when there is no budget. warningCount and
 * overCount are the number of lines with the status "WARNING" and "OVER". budgets holds one line per
 * budget: the exceeded budgets, then the budgets close to the limit, then the others, each group by
 * percent used, the highest first.
 *
 * otherCount is the number of categories without budget that have spending, otherSpent what was
 * spent in them and others holds the 10 with most spending, the first one being the biggest.
 */
public record BudgetTrackingResponse(
        String month,
        boolean currentMonth,
        String previousMonth,
        String nextMonth,
        int dayOfMonth,
        int daysInMonth,
        int daysLeft,
        int ticketCount,
        BigDecimal monthSpent,
        BigDecimal budgetTotal,
        BigDecimal budgetSpent,
        BigDecimal budgetRemaining,
        BigDecimal percentUsed,
        int warningCount,
        int overCount,
        List<BudgetTrackingLineResponse> budgets,
        int otherCount,
        BigDecimal otherSpent,
        List<BudgetTrackingOtherResponse> others
) {
}
