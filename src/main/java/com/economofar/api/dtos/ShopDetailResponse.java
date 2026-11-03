package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * One shop of the period. overallAverageBasket is the average ticket of all the tickets of the period that
 * have a shop (the ones without a shop name that can be read are left out) and basketGapPercent how far
 * the average ticket of this shop is from it, in percent (negative when it is smaller). monthly has every month of the period, oldest first, the months without purchase included.
 * categories holds 10 at most, the biggest first (categoryTotal is how many there are). weekdayVisitDays
 * has seven numbers, Monday to Sunday: the different days with a purchase for each day of the week.
 * tickets are the latest 20, the newest first (hiddenTicketCount is how many more there are).
 */
public record ShopDetailResponse(
        int months,
        String from,
        String to,
        ShopResponse shop,
        BigDecimal overallAverageBasket,
        BigDecimal basketGapPercent,
        List<ShopMonthResponse> monthly,
        int categoryTotal,
        List<ShopCategoryResponse> categories,
        List<Integer> weekdayVisitDays,
        int hiddenTicketCount,
        List<ShopTicketResponse> tickets
) {
}
