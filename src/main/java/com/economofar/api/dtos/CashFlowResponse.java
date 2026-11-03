package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Income against spending for the last months, the oldest first. The averages only use
 * the finished months that hold some data (completedMonths of them). hasIncome tells if the
 * user declared at least one income.
 */
public record CashFlowResponse(
        List<CashFlowMonthResponse> months,
        BigDecimal averageIncome,
        BigDecimal averageExpenses,
        BigDecimal averageNet,
        Double averageSavingsRate,
        int completedMonths,
        boolean hasIncome
) {
}
