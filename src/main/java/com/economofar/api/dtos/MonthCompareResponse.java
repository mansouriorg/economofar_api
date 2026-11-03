package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * A month compared with the month before it.
 *
 * month and previousMonth are yyyy-MM. When partial is true the chosen month is the month in
 * progress: its first days (days, from the 1st to today) are compared with the same days of the
 * previous month (previousDays, fewer when the previous month is shorter), so that an early month
 * is not compared with a whole one; previousFullTotal is the whole previous month. For a finished
 * month, partial is false and both months are taken whole.
 *
 * currentTotal and previousTotal are the amounts spent in the compared days, delta is
 * currentTotal minus previousTotal and deltaPercent is delta as a percentage of previousTotal
 * (one decimal), null when previousTotal is zero. The averages are the amount of a ticket (null
 * without ticket).
 *
 * categories are sorted by the size of the change, the biggest first; when there are more than 9
 * categories the 8 with the biggest change are listed and the others are added up in one last
 * line called "Autres catégories". categoryCount is the number of categories before that. points
 * has one entry per day of the longest of the two months.
 */
public record MonthCompareResponse(
        String month,
        String previousMonth,
        boolean partial,
        int days,
        int previousDays,
        BigDecimal currentTotal,
        BigDecimal previousTotal,
        BigDecimal previousFullTotal,
        BigDecimal delta,
        BigDecimal deltaPercent,
        int currentTickets,
        int previousTickets,
        BigDecimal currentAverage,
        BigDecimal previousAverage,
        int categoryCount,
        List<MonthCompareCategoryResponse> categories,
        List<MonthComparePointResponse> points
) {
}
