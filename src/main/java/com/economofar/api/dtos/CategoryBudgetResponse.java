package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One category limit with what has been spent on it this month.
 */
public record CategoryBudgetResponse(
        Long id,
        String category,
        BigDecimal monthlyAmount,
        BigDecimal spent,
        BigDecimal remaining,
        double percentUsed,
        boolean exceeded
) {
}
