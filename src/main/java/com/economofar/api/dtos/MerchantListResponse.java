package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shops where the user spent money, the biggest first.
 *
 * from and to are the first and the last day of the period (yyyy-MM-dd). total is the spending of
 * all the shops, merchantCount is the number of different shops (merchants only lists the biggest
 * ones, at most 50) and tickets is the number of receipts. The shares of the shops listed do not
 * add up to 100 when some shops are left out.
 */
public record MerchantListResponse(
        int months,
        String from,
        String to,
        BigDecimal total,
        int tickets,
        int merchantCount,
        List<MerchantSummaryResponse> merchants
) {
}
