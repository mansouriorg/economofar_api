package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * Where an article was bought. averagePrice is the unit price paid there over the period (the amount
 * spent divided by the quantity, to the cent), lastPrice the unit price of the latest day it was bought
 * there, lastDate that day ("2026-10-17"). cheapest is true for the shops with the lowest average
 * price when another shop is more expensive.
 */
public record ItemPriceMerchantResponse(
        String name,
        int purchaseCount,
        long quantity,
        BigDecimal averagePrice,
        BigDecimal lastPrice,
        String lastDate,
        boolean cheapest
) {
}
