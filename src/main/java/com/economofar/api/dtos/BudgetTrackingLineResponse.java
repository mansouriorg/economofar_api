package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The budget of one category and what was spent in it during the month.
 *
 * key is the name without the spaces around it and without capitals, and name is the text of the
 * budget as it was saved. remaining is the budget minus what was spent, negative when the budget is
 * exceeded. percentUsed is spent divided by the budget, in percent with one decimal, and it can be
 * above 100. status is "OK", "WARNING" (80 percent of the budget or more, up to the budget) or "OVER"
 * (more than the budget). ticketCount is the number of tickets of the category in the month and
 * recent holds 10 of them at most, those of the latest days, the most recent day first.
 *
 * projected is the spending the month would reach at the same daily pace, and projectedOver tells
 * that it is above the budget; projected is null for a month that has ended and when the current
 * month is too young (before the 5th day) or nothing was spent. dailyAllowance is what can still be
 * spent each day until the end of the month, today included; it is null for a month that has ended
 * and when nothing remains.
 */
public record BudgetTrackingLineResponse(
        String key,
        String name,
        BigDecimal budget,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentUsed,
        String status,
        int ticketCount,
        BigDecimal projected,
        boolean projectedOver,
        BigDecimal dailyAllowance,
        List<BudgetTrackingTicketResponse> recent
) {
}
