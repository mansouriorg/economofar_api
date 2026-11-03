package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One day of the week over the period. dayOfWeek goes from 1 (Monday) to 7 (Sunday), days is how
 * many of these days the period contains, activeDays how many of them have at least one purchase,
 * average is total divided by days and share is the part of all the spending, in percent.
 */
public record WeekdayRhythmResponse(
        int dayOfWeek,
        BigDecimal total,
        BigDecimal average,
        BigDecimal share,
        int purchaseCount,
        int activeDays,
        int days
) {
}
