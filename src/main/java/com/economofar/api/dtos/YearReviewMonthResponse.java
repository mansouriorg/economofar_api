package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One month of the year review: month goes from 1 to 12, total is the amount spent and tickets
 * the number of tickets. A month that has not started yet has a total of zero.
 */
public record YearReviewMonthResponse(
        int month,
        BigDecimal total,
        int tickets
) {
}
