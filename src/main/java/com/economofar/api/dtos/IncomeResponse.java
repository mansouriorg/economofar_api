package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * date and endDate are ISO dates (yyyy-MM-dd), endDate is null when the income has no end.
 * countsThisMonth tells if the income is part of the current month.
 */
public record IncomeResponse(
        Long id,
        String name,
        BigDecimal amount,
        String date,
        boolean recurring,
        String endDate,
        boolean countsThisMonth
) {
}
