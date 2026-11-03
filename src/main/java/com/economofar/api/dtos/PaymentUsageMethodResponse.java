package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the tickets of the period say about one payment method.
 *
 * key is the name without case, accents and punctuation: it does not change when the method is
 * written in another way, and name is the way it is written most often. amount adds the amounts
 * above zero. average is amount divided by the tickets that have an amount (null when none has) and
 * share is the percent of all the spending of the period, with one decimal (null when nothing was
 * spent). months holds every calendar month of the period, the oldest first. categories holds the 5
 * categories with most spending among categoryCount, and recent 10 tickets at most, those of the
 * latest days: the most recent day first, and the biggest amount first within a day.
 */
public record PaymentUsageMethodResponse(
        String key,
        String name,
        int ticketCount,
        BigDecimal amount,
        BigDecimal average,
        BigDecimal share,
        String firstDate,
        String lastDate,
        int daysSinceLast,
        List<PaymentUsageMonthResponse> months,
        int categoryCount,
        List<PaymentUsageCategoryResponse> categories,
        List<PaymentUsageTicketResponse> recent
) {
}
