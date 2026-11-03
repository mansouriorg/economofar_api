package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * date is an ISO string (yyyy-MM-dd). A negative amount is a withdrawal.
 */
public record SavingsContributionResponse(
        Long id,
        BigDecimal amount,
        String date,
        String note
) {
}
