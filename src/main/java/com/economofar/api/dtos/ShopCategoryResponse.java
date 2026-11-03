package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A category bought in a shop: what was spent, its tickets and its share of the spending in the shop, in percent. */
public record ShopCategoryResponse(String name, BigDecimal spent, int ticketCount, BigDecimal sharePercent) {
}
