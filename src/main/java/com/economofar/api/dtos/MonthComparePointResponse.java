package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One day of the two cumulative curves. day goes from 1 to the length of the longest of the two
 * months. current is the total spent from the 1st to this day in the chosen month, null after the
 * last compared day (after today for the month in progress, after the last day of a shorter
 * month). previous is the same for the previous month, null after its last day.
 */
public record MonthComparePointResponse(
        int day,
        BigDecimal current,
        BigDecimal previous
) {
}
