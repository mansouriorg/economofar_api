package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the tickets of the period say about one category.
 *
 * key is the name without case, accents and punctuation: it does not change when the category is
 * written in another way. ticketCount is the number of tickets and activeDays the number of
 * different days they are dated; total adds the amounts above zero. average is total divided by the
 * number of tickets that have an amount (null when none has) and share is total as a percentage of
 * all the spending of the period, with one decimal (null when nothing was spent). firstDate and
 * lastDate are ISO dates and daysSinceLast the days from the last ticket to today (0 when it is dated
 * today). daysBetweenPurchases is the span between the first and the last ticket divided by the
 * number of gaps between the days with a ticket, rounded to a whole day (null with fewer than 2 such
 * days).
 *
 * merchantCount is the number of known merchants of the category and unknownCount the number of
 * tickets without a known merchant. merchants holds the 5 entries with most spending and recent the
 * 10 latest tickets, the most recent first.
 */
public record CategoryProfileResponse(
        String key,
        String category,
        int ticketCount,
        int activeDays,
        BigDecimal total,
        BigDecimal average,
        BigDecimal share,
        String firstDate,
        String lastDate,
        int daysSinceLast,
        Integer daysBetweenPurchases,
        int merchantCount,
        int unknownCount,
        List<CategoryMerchantResponse> merchants,
        List<CategoryTicketResponse> recent
) {
}
