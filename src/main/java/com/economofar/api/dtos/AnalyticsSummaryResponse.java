package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending analytics for a period of N months (including the current month).
 */
public record AnalyticsSummaryResponse(
        int months,
        String rangeStart,
        String rangeEnd,
        BigDecimal totalSpent,
        long ticketCount,
        BigDecimal averageTicket,
        List<CategoryTotal> categories,
        List<MonthlyTotal> monthly,
        List<NamedTotal> topMerchants,
        List<NamedTotal> paymentMethods
) {

    public record CategoryTotal(
            String name,
            BigDecimal total,
            long count,
            double percentage
    ) {
    }

    public record MonthlyTotal(
            String month,
            BigDecimal total,
            long count
    ) {
    }

    public record NamedTotal(
            String name,
            BigDecimal total,
            long count
    ) {
    }
}
