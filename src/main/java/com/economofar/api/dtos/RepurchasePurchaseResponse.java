package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One line of a ticket where the article was bought. id is the ticket (empty when unknown), date is
 * "yyyy-MM-dd", quantity at least 1, amount the price of the line (0.00 without a price), shop the shop name
 * written on the ticket, empty when none can be read.
 */
public record RepurchasePurchaseResponse(
        String id,
        String date,
        int quantity,
        BigDecimal amount,
        String shop
) {
}
