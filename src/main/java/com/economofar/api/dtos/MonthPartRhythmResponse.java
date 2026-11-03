package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One part of the month over the period: "start" (days 1 to 10), "middle" (11 to 20) or "end"
 * (21 to the last day). days is how many calendar days of the period fall in that part,
 * dailyAverage is total divided by days and share is the part of all the spending, in percent.
 */
public record MonthPartRhythmResponse(
        String key,
        int days,
        BigDecimal total,
        BigDecimal dailyAverage,
        BigDecimal share
) {
}
