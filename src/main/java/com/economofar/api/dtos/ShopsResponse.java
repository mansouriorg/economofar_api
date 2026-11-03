package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shops where the person spent in the period (the current month and the months before it, 3 to 24),
 * the biggest spending first, 100 at most (hiddenCount is how many more there are). Tickets without a
 * shop name that can be read are not shops: unknownSpent and unknownTicketCount say how much they weigh,
 * and totalSpent and ticketCount count them. topShopsSharePercent is the part of the three biggest shops.
 * hasBaseline tells that tickets were saved on 3 different days before the last 90 days, without which no
 * shop is said to be new. The four counts are about all the shops of the period, not only the 100 listed;
 * a shop that was left since 90 days is only counted when it has a ticket in the period, so fadingCount is
 * complete from 7 months.
 */
public record ShopsResponse(
        int months,
        String from,
        String to,
        BigDecimal totalSpent,
        int ticketCount,
        int shopCount,
        BigDecimal unknownSpent,
        int unknownTicketCount,
        BigDecimal topShopsSharePercent,
        boolean hasBaseline,
        int newCount,
        int fadingCount,
        int upCount,
        int downCount,
        int hiddenCount,
        List<ShopResponse> shops
) {
}
