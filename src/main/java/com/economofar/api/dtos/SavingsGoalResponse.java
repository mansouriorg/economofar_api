package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * A savings goal with its progress.
 *
 * status is "completed", "overdue", "notStarted", "noDeadline", "onTrack" or "behind".
 * deadline and projectedDate are ISO dates (yyyy-MM-dd). daysLeft is negative when the date
 * has passed. monthlyNeeded is what must be put aside each month to reach the goal on time,
 * monthlyPace is the average net amount put aside per month recently.
 * contributions holds the most recent ones, the newest first.
 */
public record SavingsGoalResponse(
        Long id,
        String name,
        String icon,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        BigDecimal remainingAmount,
        double percent,
        String deadline,
        Long daysLeft,
        BigDecimal monthlyNeeded,
        BigDecimal monthlyPace,
        String projectedDate,
        String status,
        int contributionCount,
        List<SavingsContributionResponse> contributions
) {
}
