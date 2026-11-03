package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Progress of a savings goal.
 *
 * saved       = sum of the contributions (never below 0)
 * remaining   = target - saved (never below 0)
 * monthlyNeeded = remaining divided by the whole months left before the deadline (at least 1)
 * monthlyPace = net amount put aside during the last 180 days, expressed per month. The period
 *               counts for at least 30 days so that a single deposit does not look like a habit.
 * projectedDate = today + the time needed to save the remaining amount at that pace
 *               (nothing when it would take more than 10 years)
 *
 * status: completed, overdue (deadline passed), notStarted (nothing saved yet), noDeadline,
 * onTrack (projected date on or before the deadline) or behind.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class SavingsGoalCalculator {

    public record Deposit(BigDecimal amount, LocalDate date) {
    }

    public record Progress(
            BigDecimal saved,
            BigDecimal remaining,
            double percent,
            Long daysLeft,
            BigDecimal monthlyNeeded,
            BigDecimal monthlyPace,
            LocalDate projectedDate,
            String status
    ) {
    }

    static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.4375");
    static final int PACE_WINDOW_DAYS = 180;
    static final int MIN_PACE_DAYS = 30;
    static final long MAX_PROJECTION_DAYS = 3650;

    private SavingsGoalCalculator() {
    }

    public static Progress compute(BigDecimal target, LocalDate deadline, List<Deposit> deposits, LocalDate today) {
        BigDecimal safeTarget = target == null || target.signum() <= 0 ? BigDecimal.ZERO : target;

        BigDecimal saved = BigDecimal.ZERO;
        LocalDate firstDate = null;

        if (deposits != null) {
            for (Deposit deposit : deposits) {
                if (deposit == null || deposit.amount() == null) {
                    continue;
                }

                saved = saved.add(deposit.amount());

                if (deposit.date() != null && (firstDate == null || deposit.date().isBefore(firstDate))) {
                    firstDate = deposit.date();
                }
            }
        }

        if (saved.signum() < 0) {
            saved = BigDecimal.ZERO;
        }

        BigDecimal remaining = safeTarget.subtract(saved);

        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }

        double percent = 0;

        if (safeTarget.signum() > 0) {
            percent = Math.min(
                    100.0,
                    saved.multiply(BigDecimal.valueOf(100))
                            .divide(safeTarget, 1, RoundingMode.HALF_UP)
                            .doubleValue()
            );
        }

        boolean completed = safeTarget.signum() > 0 && saved.compareTo(safeTarget) >= 0;

        if (!completed && percent >= 100.0) {
            percent = 99.9;
        }

        Long daysLeft = deadline == null ? null : ChronoUnit.DAYS.between(today, deadline);

        BigDecimal monthlyNeeded = null;

        if (!completed && daysLeft != null && daysLeft >= 0) {
            BigDecimal months = BigDecimal.valueOf(daysLeft).divide(DAYS_PER_MONTH, 0, RoundingMode.CEILING);

            if (months.signum() <= 0) {
                months = BigDecimal.ONE;
            }

            monthlyNeeded = remaining.divide(months, 2, RoundingMode.CEILING);
        }

        BigDecimal monthlyPace = null;

        if (firstDate != null) {
            LocalDate windowStart = today.minusDays(PACE_WINDOW_DAYS);
            LocalDate trackedSince = firstDate.isAfter(windowStart) ? firstDate : windowStart;

            BigDecimal net = BigDecimal.ZERO;

            for (Deposit deposit : deposits) {
                if (deposit == null || deposit.amount() == null || deposit.date() == null) {
                    continue;
                }

                if (!deposit.date().isBefore(trackedSince) && !deposit.date().isAfter(today)) {
                    net = net.add(deposit.amount());
                }
            }

            long days = Math.max(MIN_PACE_DAYS, ChronoUnit.DAYS.between(trackedSince, today) + 1);

            BigDecimal pace = net
                    .multiply(DAYS_PER_MONTH)
                    .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

            if (pace.signum() > 0) {
                monthlyPace = pace;
            }
        }

        LocalDate projectedDate = null;

        if (!completed && monthlyPace != null && remaining.signum() > 0) {
            BigDecimal daysToGo = remaining
                    .multiply(DAYS_PER_MONTH)
                    .divide(monthlyPace, 0, RoundingMode.CEILING);

            if (daysToGo.compareTo(BigDecimal.valueOf(MAX_PROJECTION_DAYS)) <= 0) {
                projectedDate = today.plusDays(daysToGo.longValue());
            }
        }

        String status;

        if (completed) {
            status = "completed";
        } else if (daysLeft != null && daysLeft < 0) {
            status = "overdue";
        } else if (saved.signum() == 0) {
            status = "notStarted";
        } else if (deadline == null) {
            status = "noDeadline";
        } else if (projectedDate != null && !projectedDate.isAfter(deadline)) {
            status = "onTrack";
        } else {
            status = "behind";
        }

        return new Progress(
                saved.setScale(2, RoundingMode.HALF_UP),
                remaining.setScale(2, RoundingMode.HALF_UP),
                percent,
                daysLeft,
                monthlyNeeded,
                monthlyPace,
                projectedDate,
                status
        );
    }
}
