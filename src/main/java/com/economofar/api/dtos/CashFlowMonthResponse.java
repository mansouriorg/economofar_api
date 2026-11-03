package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One month of the cash-flow. month is yyyy-MM. net is income minus expenses.
 * savingsRate is the part of the income that was not spent, in percent (negative when the
 * month cost more than it brought), or null when there was no income.
 * current is true for the month that is not finished yet.
 */
public record CashFlowMonthResponse(
        String month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal net,
        Double savingsRate,
        boolean current
) {
}
