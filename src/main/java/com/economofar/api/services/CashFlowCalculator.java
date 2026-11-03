package com.economofar.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Income against spending, month by month.
 *
 * A one-off income counts in the month of its date. A recurring income counts in every month
 * from the month of its start date until the month of its end date (for ever without end date).
 *
 * For each month: net = income - expenses, and savingsRate = net / income in percent (null
 * without income). The averages only use the finished months that hold some data.
 *
 * This class has no dependency on Spring so that it can be tested alone.
 */
public final class CashFlowCalculator {

    public record Source(BigDecimal amount, LocalDate startDate, boolean recurring, LocalDate endDate) {
    }

    public record Spend(BigDecimal amount, LocalDate date) {
    }

    public record MonthFlow(
            YearMonth month,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal net,
            Double savingsRate,
            boolean current
    ) {
    }

    public record Report(
            List<MonthFlow> months,
            BigDecimal averageIncome,
            BigDecimal averageExpenses,
            BigDecimal averageNet,
            Double averageSavingsRate,
            int completedMonths
    ) {
    }

    static final int MIN_MONTHS = 1;
    static final int MAX_MONTHS = 12;

    private CashFlowCalculator() {
    }

    public static boolean counts(Source source, YearMonth month) {
        if (source == null || month == null || source.amount() == null || source.startDate() == null) {
            return false;
        }

        if (source.amount().signum() <= 0) {
            return false;
        }

        YearMonth start = YearMonth.from(source.startDate());

        if (!source.recurring()) {
            return month.equals(start);
        }

        if (month.isBefore(start)) {
            return false;
        }

        if (source.endDate() == null) {
            return true;
        }

        return !month.isAfter(YearMonth.from(source.endDate()));
    }

    public static BigDecimal incomeFor(List<Source> sources, YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;

        if (sources == null) {
            return total;
        }

        for (Source source : sources) {
            if (counts(source, month)) {
                total = total.add(source.amount());
            }
        }

        return total;
    }

    public static Report compute(List<Source> sources, List<Spend> spends, YearMonth current, int requestedMonths) {
        int count = Math.min(Math.max(requestedMonths, MIN_MONTHS), MAX_MONTHS);
        YearMonth first = current.minusMonths(count - 1L);

        Map<YearMonth, BigDecimal> spent = new HashMap<>();

        if (spends != null) {
            for (Spend spend : spends) {
                if (spend == null || spend.amount() == null || spend.date() == null) {
                    continue;
                }

                YearMonth month = YearMonth.from(spend.date());

                if (month.isBefore(first) || month.isAfter(current)) {
                    continue;
                }

                spent.merge(month, spend.amount(), BigDecimal::add);
            }
        }

        List<MonthFlow> months = new ArrayList<>();

        BigDecimal sumIncome = BigDecimal.ZERO;
        BigDecimal sumExpenses = BigDecimal.ZERO;
        BigDecimal sumIncomeWithIncome = BigDecimal.ZERO;
        BigDecimal sumExpensesWithIncome = BigDecimal.ZERO;
        int completed = 0;

        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            BigDecimal income = incomeFor(sources, month).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expenses = spent.getOrDefault(month, BigDecimal.ZERO);

            if (expenses.signum() < 0) {
                expenses = BigDecimal.ZERO;
            }

            expenses = expenses.setScale(2, RoundingMode.HALF_UP);

            BigDecimal net = income.subtract(expenses);
            boolean isCurrent = month.equals(current);

            months.add(new MonthFlow(month, income, expenses, net, rate(net, income), isCurrent));

            boolean hasData = income.signum() > 0 || expenses.signum() > 0;

            if (!isCurrent && hasData) {
                completed++;
                sumIncome = sumIncome.add(income);
                sumExpenses = sumExpenses.add(expenses);

                if (income.signum() > 0) {
                    sumIncomeWithIncome = sumIncomeWithIncome.add(income);
                    sumExpensesWithIncome = sumExpensesWithIncome.add(expenses);
                }
            }
        }

        BigDecimal averageIncome = BigDecimal.ZERO.setScale(2);
        BigDecimal averageExpenses = BigDecimal.ZERO.setScale(2);
        BigDecimal averageNet = BigDecimal.ZERO.setScale(2);

        if (completed > 0) {
            BigDecimal divisor = BigDecimal.valueOf(completed);

            averageIncome = sumIncome.divide(divisor, 2, RoundingMode.HALF_UP);
            averageExpenses = sumExpenses.divide(divisor, 2, RoundingMode.HALF_UP);
            averageNet = sumIncome.subtract(sumExpenses).divide(divisor, 2, RoundingMode.HALF_UP);
        }

        Double averageRate = rate(sumIncomeWithIncome.subtract(sumExpensesWithIncome), sumIncomeWithIncome);

        return new Report(months, averageIncome, averageExpenses, averageNet, averageRate, completed);
    }

    private static Double rate(BigDecimal net, BigDecimal income) {
        if (income.signum() <= 0) {
            return null;
        }

        return net.multiply(BigDecimal.valueOf(100))
                .divide(income, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
