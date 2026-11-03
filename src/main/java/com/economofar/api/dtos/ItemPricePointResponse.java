package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * The unit price of an article on one day: price is the amount spent that day divided by the quantity
 * bought, to the cent. merchant is the name of the shop when exactly one named shop was used that day,
 * merchantCount the number of named shops (0 when the shop is not known).
 */
public record ItemPricePointResponse(
        String date,
        BigDecimal price,
        long quantity,
        String merchant,
        int merchantCount
) {
}
