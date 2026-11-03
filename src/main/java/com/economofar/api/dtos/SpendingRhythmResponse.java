package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * When the user spends: the days of the week, the parts of the month and the days without
 * purchase.
 *
 * from and to are the first and the last day of the period asked for (yyyy-MM-dd). The figures
 * only use the days since the first purchase of the period (trackedFrom, null when there is none)
 * so that the days before the user started to record purchases do not count as days without
 * purchase; trackedDays is their number. enoughHistory is true from 14 tracked days: only then
 * are busiestDay, quietestDay and peakRatio given. busiestDay and quietestDay are days of the week
 * from 1 (Monday) to 7 (Sunday) and peakRatio is the daily average of the busiest day of the
 * week divided by the daily average of the period (1.0 means a perfectly even week).
 *
 * weekdays always has 7 entries (Monday first) and monthParts 3 (start, middle, end).
 * biggestDayDate and biggestDayAmount describe the day with the most spending (the latest one
 * when several are equal). currentStreak is the number of days in a row without purchase up to
 * today (0 when something was bought today, today counted otherwise) and longestStreak the
 * longest such run of the tracked days.
 */
public record SpendingRhythmResponse(
        int weeks,
        String from,
        String to,
        String trackedFrom,
        int trackedDays,
        boolean enoughHistory,
        BigDecimal total,
        int purchaseCount,
        int activeDays,
        int noPurchaseDays,
        BigDecimal dailyAverage,
        List<WeekdayRhythmResponse> weekdays,
        Integer busiestDay,
        Integer quietestDay,
        BigDecimal peakRatio,
        BigDecimal weekendTotal,
        BigDecimal weekendShare,
        BigDecimal weekendDailyAverage,
        BigDecimal weekdayDailyAverage,
        List<MonthPartRhythmResponse> monthParts,
        String biggestDayDate,
        BigDecimal biggestDayAmount,
        int currentStreak,
        int longestStreak
) {
}
