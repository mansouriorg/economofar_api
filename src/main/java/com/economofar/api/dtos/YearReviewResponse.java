package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * A year of spending.
 *
 * When partial is true the year is the year in progress: monthsElapsed is the number of the
 * current month and the first part of the previous year (up to the same day) is the one compared
 * with it, so that the year is not compared with a whole one; previousFullTotal is the whole
 * previous year. A finished year is compared with the whole year before it.
 *
 * delta is total minus previousTotal and deltaPercent is delta as a percentage of previousTotal
 * (one decimal), null when previousTotal is zero. averageTicket is the amount of a ticket (null
 * without ticket).
 *
 * trackedMonths are the finished months from the first month with a ticket of the year (the
 * month in progress does not count), monthlyAverage is their average spending (null without
 * tracked month) and busiestMonth and quietestMonth (1 to 12) are the most and the least
 * expensive of them (null below 2 tracked months, the first one when several are equal).
 *
 * months always has the 12 months. categories are sorted by total, the biggest first; with more
 * than 7 categories the 6 biggest are listed and the others are added up in one last line called
 * "Autres catégories"; categoryCount is the number before that. merchants are the 5 shops where
 * the most was spent (unknown shops are left out) and merchantCount the number of known shops.
 * biggestTicket is null without ticket.
 */
public record YearReviewResponse(
        int year,
        boolean partial,
        int monthsElapsed,
        BigDecimal total,
        int tickets,
        BigDecimal averageTicket,
        BigDecimal previousTotal,
        BigDecimal previousFullTotal,
        BigDecimal delta,
        BigDecimal deltaPercent,
        int trackedMonths,
        BigDecimal monthlyAverage,
        Integer busiestMonth,
        Integer quietestMonth,
        List<YearReviewMonthResponse> months,
        int categoryCount,
        List<YearReviewCategoryResponse> categories,
        int merchantCount,
        List<YearReviewMerchantResponse> merchants,
        YearReviewTicketResponse biggestTicket
) {
}
