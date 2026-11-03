package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A regular payment expected before the end of the month. date is an ISO string (yyyy-MM-dd).
 */
public record ForecastUpcomingResponse(
        String merchantName,
        BigDecimal amount,
        String date
) {
}
