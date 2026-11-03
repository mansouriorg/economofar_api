package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A shop of the period. key is the way the shop is recognised (the detail takes it back), name the spelling
 * written most often. spent and ticketCount are about the period; visitDays the different days with a
 * purchase; averageBasket the spending divided by the tickets; sharePercent the part of all the spending of
 * the period, null when nothing was spent; firstDate and lastDate are "yyyy-MM-dd"; daysSinceLast the days
 * between the last purchase and today; averageGapDays the days between two visits on average, null with
 * a single visit day.
 *
 * recentSpent is the spending of the last 90 days and previousSpent the one of the 90 days before them,
 * changePercent the change from the second to the first, null when nothing was spent in the second.
 * status is "NEW" (first purchase of the last 24 months within the last 90 days, when tickets were saved on
 * 3 different days before them), "FADING" (two visit days or more in the 90 days before, none since), "UP"
 * or "DOWN" (a quarter more or less, 10 euros at least) or "STEADY". The status does not depend on the
 * period, but a shop is only listed when it has a ticket in the period.
 */
public record ShopResponse(
        String key,
        String name,
        BigDecimal spent,
        BigDecimal sharePercent,
        int ticketCount,
        int visitDays,
        BigDecimal averageBasket,
        String firstDate,
        String lastDate,
        int daysSinceLast,
        BigDecimal averageGapDays,
        BigDecimal recentSpent,
        BigDecimal previousSpent,
        BigDecimal changePercent,
        String status
) {
}
