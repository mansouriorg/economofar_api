package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * How the spending of one category moved over the months of the window.
 *
 * totals has one amount per month of the window (the oldest first). recentAverage and
 * earlierAverage are the monthly averages of the most recent months and of the same number of
 * months before them (null when the history is too short to compare). changePercent is the change
 * from the earlier months to the recent ones, null when it cannot be calculated.
 * trend is UP, DOWN, STABLE, NEW (nothing spent in the earlier months), GONE (nothing spent in
 * the recent months) or NONE (not enough history, or nothing spent in the compared months).
 */
public record CategoryTrendResponse(
        String name,
        List<BigDecimal> totals,
        BigDecimal total,
        BigDecimal monthlyAverage,
        BigDecimal recentAverage,
        BigDecimal earlierAverage,
        BigDecimal changePercent,
        String trend
) {
}
