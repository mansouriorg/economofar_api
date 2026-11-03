package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

public record RecurringExpensesResponse(
        List<RecurringExpenseResponse> items,
        BigDecimal monthlyTotal
) {
}
