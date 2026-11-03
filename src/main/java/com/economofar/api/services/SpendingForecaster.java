package com.economofar.api.services;

import com.economofar.api.dtos.ForecastUpcomingResponse;
import com.economofar.api.dtos.SpendingForecastResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Estimates what will have been spent at the end of the current month.
 *
 * The spending is split in two parts.
 *
 * Regular payments (subscriptions, rent...) are known: the ones still expected before the end of
 * the month are added with their usual amount and date.
 *
 * Everything else follows a daily pace. The pace is a mix of the current month (trusted more and
 * more as the month goes on) and the average of the last three months. During the first week the
 * current month is treated as if it already lasted 7 days, so one early purchase does not look
 * like a daily habit.
 *
 * estimate = spent so far + regular payments to come + daily pace x days left
 *
 * The range shown to the user is the variable part times 0.8 and times 1.25.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class SpendingForecaster {

    public record Spend(String merchant, BigDecimal amount, LocalDate date) {
    }

    public record Recurring(String merchant, BigDecimal amount, LocalDate nextDate, int intervalDays) {
    }

    private record Event(String merchant, BigDecimal amount, LocalDate date) {
    }

    static final int HISTORY_MONTHS = 3;
    static final int MIN_PACE_DAYS = 7;
    static final int LATE_TOLERANCE_DAYS = 7;
    static final int MAX_UPCOMING_SHOWN = 10;
    static final int MIN_CONTAINS_LENGTH = 5;
    static final int ONE_PER_MONTH_FROM_DAYS = 26;
    static final BigDecimal LOW_FACTOR = new BigDecimal("0.80");
    static final BigDecimal HIGH_FACTOR = new BigDecimal("1.25");
    static final BigDecimal AT_RISK_SHARE = new BigDecimal("0.90");

    private SpendingForecaster() {
    }

    public static SpendingForecastResponse forecast(
            List<Spend> spends,
            List<Recurring> recurring,
            BigDecimal monthlyBudget,
            LocalDate today
    ) {
        LocalDate monthStart = today.withDayOfMonth(1);
        int daysInMonth = today.lengthOfMonth();
        int dayOfMonth = today.getDayOfMonth();
        int daysRemaining = daysInMonth - dayOfMonth;
        LocalDate monthEnd = today.withDayOfMonth(daysInMonth);
        LocalDate previousStart = monthStart.minusMonths(1);

        List<Spend> clean = cleanSpends(spends, today);
        List<String> recurringKeys = recurringKeys(recurring);

        LocalDate firstDate = null;

        for (Spend spend : clean) {
            if (firstDate == null || spend.date().isBefore(firstDate)) {
                firstDate = spend.date();
            }
        }

        BigDecimal spentSoFar = BigDecimal.ZERO;
        BigDecimal recurringSoFar = BigDecimal.ZERO;
        BigDecimal previousMonthTotal = BigDecimal.ZERO;

        for (Spend spend : clean) {
            if (!spend.date().isBefore(monthStart)) {
                spentSoFar = spentSoFar.add(spend.amount());

                if (isRecurring(spend.merchant(), recurringKeys)) {
                    recurringSoFar = recurringSoFar.add(spend.amount());
                }
            } else if (!spend.date().isBefore(previousStart)) {
                previousMonthTotal = previousMonthTotal.add(spend.amount());
            }
        }

        BigDecimal variableSoFar = spentSoFar.subtract(recurringSoFar);

        int elapsed = 1;

        if (firstDate != null) {
            LocalDate trackedSince = firstDate.isAfter(monthStart) ? firstDate : monthStart;
            elapsed = (int) Math.max(1, ChronoUnit.DAYS.between(trackedSince, today) + 1);
        }

        BigDecimal currentRate = variableSoFar.divide(
                BigDecimal.valueOf(Math.max(elapsed, MIN_PACE_DAYS)),
                6,
                RoundingMode.HALF_UP
        );

        BigDecimal historicVariable = BigDecimal.ZERO;
        long historicDays = 0;
        int monthsWithData = 0;

        for (int back = 1; back <= HISTORY_MONTHS; back++) {
            LocalDate start = monthStart.minusMonths(back);
            LocalDate end = start.plusMonths(1).minusDays(1);

            if (firstDate == null || firstDate.isAfter(end)) {
                continue;
            }

            LocalDate trackedSince = firstDate.isAfter(start) ? firstDate : start;
            historicDays += ChronoUnit.DAYS.between(trackedSince, end) + 1;

            boolean anySpend = false;

            for (Spend spend : clean) {
                if (!spend.date().isBefore(start) && !spend.date().isAfter(end)) {
                    anySpend = true;

                    if (!isRecurring(spend.merchant(), recurringKeys)) {
                        historicVariable = historicVariable.add(spend.amount());
                    }
                }
            }

            if (anySpend) {
                monthsWithData++;
            }
        }

        BigDecimal rate = currentRate;

        if (historicDays > 0) {
            BigDecimal historicRate = historicVariable.divide(
                    BigDecimal.valueOf(historicDays),
                    6,
                    RoundingMode.HALF_UP
            );

            BigDecimal weight = BigDecimal.valueOf(Math.min(elapsed, daysInMonth))
                    .divide(BigDecimal.valueOf(daysInMonth), 6, RoundingMode.HALF_UP);

            rate = weight.multiply(currentRate)
                    .add(BigDecimal.ONE.subtract(weight).multiply(historicRate))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        List<Event> events = upcomingEvents(recurring, today, monthEnd);

        BigDecimal upcomingTotal = BigDecimal.ZERO;

        for (Event event : events) {
            upcomingTotal = upcomingTotal.add(event.amount());
        }

        BigDecimal projectedVariable = rate.multiply(BigDecimal.valueOf(daysRemaining));
        BigDecimal base = spentSoFar.add(upcomingTotal);

        BigDecimal projectedTotal = base.add(projectedVariable).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedLow = base.add(projectedVariable.multiply(LOW_FACTOR)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedHigh = base.add(projectedVariable.multiply(HIGH_FACTOR)).setScale(2, RoundingMode.HALF_UP);

        String budgetStatus = "none";
        BigDecimal budget = null;
        BigDecimal projectedOver = null;
        BigDecimal dailyAllowance = null;
        LocalDate exceedDate = null;

        if (monthlyBudget != null && monthlyBudget.signum() > 0) {
            budget = monthlyBudget.setScale(2, RoundingMode.HALF_UP);

            if (spentSoFar.compareTo(budget) > 0) {
                budgetStatus = "exceeded";
            } else if (projectedTotal.compareTo(budget) > 0) {
                budgetStatus = "projectedOver";
            } else if (projectedTotal.compareTo(budget.multiply(AT_RISK_SHARE)) >= 0) {
                budgetStatus = "atRisk";
            } else {
                budgetStatus = "onTrack";
            }

            if (projectedTotal.compareTo(budget) > 0) {
                projectedOver = projectedTotal.subtract(budget);
            }

            if (daysRemaining > 0) {
                BigDecimal left = budget.subtract(spentSoFar).subtract(upcomingTotal);

                if (left.signum() < 0) {
                    left = BigDecimal.ZERO;
                }

                dailyAllowance = left.divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP);
            }

            if (budgetStatus.equals("projectedOver")) {
                exceedDate = findExceedDate(spentSoFar, rate, events, budget, today, monthEnd);
            }
        }

        String confidence = "low";

        if (monthsWithData >= 2 && elapsed >= MIN_PACE_DAYS) {
            confidence = "high";
        } else if (monthsWithData >= 1 || elapsed >= 10) {
            confidence = "medium";
        }

        List<ForecastUpcomingResponse> upcoming = new ArrayList<>();

        for (Event event : events) {
            if (upcoming.size() >= MAX_UPCOMING_SHOWN) {
                break;
            }

            upcoming.add(new ForecastUpcomingResponse(
                    event.merchant(),
                    event.amount().setScale(2, RoundingMode.HALF_UP),
                    event.date().toString()
            ));
        }

        return new SpendingForecastResponse(
                YearMonth.from(today).toString(),
                dayOfMonth,
                daysInMonth,
                daysRemaining,
                spentSoFar.setScale(2, RoundingMode.HALF_UP),
                upcomingTotal.setScale(2, RoundingMode.HALF_UP),
                rate.setScale(2, RoundingMode.HALF_UP),
                projectedTotal,
                projectedLow,
                projectedHigh,
                previousMonthTotal.signum() > 0 ? previousMonthTotal.setScale(2, RoundingMode.HALF_UP) : null,
                budget,
                budgetStatus,
                projectedOver,
                dailyAllowance,
                exceedDate == null ? null : exceedDate.toString(),
                confidence,
                upcoming
        );
    }

    private static List<Spend> cleanSpends(List<Spend> spends, LocalDate today) {
        List<Spend> clean = new ArrayList<>();

        if (spends == null) {
            return clean;
        }

        for (Spend spend : spends) {
            if (spend == null
                    || spend.date() == null
                    || spend.amount() == null
                    || spend.amount().signum() <= 0
                    || spend.date().isAfter(today)) {
                continue;
            }

            clean.add(spend);
        }

        return clean;
    }

    /** Also used by DailyBudgetCalculator. */
    static List<String> recurringKeys(List<Recurring> recurring) {
        List<String> keys = new ArrayList<>();

        if (recurring == null) {
            return keys;
        }

        for (Recurring item : recurring) {
            if (item == null) {
                continue;
            }

            String key = RecurringExpenseDetector.normalize(item.merchant());

            if (!key.isEmpty()) {
                keys.add(key);
            }
        }

        return keys;
    }

    /** Also used by DailyBudgetCalculator. */
    static boolean isRecurring(String merchant, List<String> recurringKeys) {
        String key = RecurringExpenseDetector.normalize(merchant);

        if (key.isEmpty()) {
            return false;
        }

        for (String recurringKey : recurringKeys) {
            if (sameShop(key, recurringKey)) {
                return true;
            }
        }

        return false;
    }

    private static boolean sameShop(String first, String second) {
        if (first.equals(second)) {
            return true;
        }

        return Math.min(first.length(), second.length()) >= MIN_CONTAINS_LENGTH
                && (first.contains(second) || second.contains(first));
    }

    /**
     * Regular payments still expected between today and the end of the month. A payment that is
     * up to a week late is counted as due today, a payment later than that is ignored. Weekly and
     * bi-weekly payments can come several times; monthly and quarterly ones only once.
     */
    private static List<Event> upcomingEvents(List<Recurring> recurring, LocalDate today, LocalDate monthEnd) {
        List<Event> events = new ArrayList<>();

        if (recurring == null) {
            return events;
        }

        for (Recurring item : recurring) {
            if (item == null
                    || item.nextDate() == null
                    || item.amount() == null
                    || item.amount().signum() <= 0
                    || item.intervalDays() <= 0
                    || item.nextDate().isBefore(today.minusDays(LATE_TOLERANCE_DAYS))) {
                continue;
            }

            if (item.nextDate().isAfter(monthEnd)) {
                continue;
            }

            String merchant = item.merchant() == null ? "" : item.merchant().trim();
            LocalDate date = item.nextDate();

            events.add(new Event(merchant, item.amount(), date.isBefore(today) ? today : date));

            if (item.intervalDays() >= ONE_PER_MONTH_FROM_DAYS) {
                continue;
            }

            do {
                date = date.plusDays(item.intervalDays());
            } while (!date.isAfter(today));

            while (!date.isAfter(monthEnd)) {
                events.add(new Event(merchant, item.amount(), date));
                date = date.plusDays(item.intervalDays());
            }
        }

        events.sort(
                Comparator.comparing(Event::date)
                        .thenComparing(Event::merchant)
        );

        return events;
    }

    /**
     * First day on which the estimated spending goes over the budget, day by day until the end
     * of the month. Null when it never does.
     */
    private static LocalDate findExceedDate(
            BigDecimal spentSoFar,
            BigDecimal rate,
            List<Event> events,
            BigDecimal budget,
            LocalDate today,
            LocalDate monthEnd
    ) {
        TreeMap<LocalDate, BigDecimal> byDate = new TreeMap<>();

        for (Event event : events) {
            byDate.merge(event.date(), event.amount(), BigDecimal::add);
        }

        BigDecimal cumulative = spentSoFar.add(byDate.getOrDefault(today, BigDecimal.ZERO));

        if (cumulative.compareTo(budget) > 0) {
            return today;
        }

        LocalDate day = today.plusDays(1);

        while (!day.isAfter(monthEnd)) {
            cumulative = cumulative.add(rate).add(byDate.getOrDefault(day, BigDecimal.ZERO));

            if (cumulative.compareTo(budget) > 0) {
                return day;
            }

            day = day.plusDays(1);
        }

        return null;
    }
}
