package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A category of the event: what was spent, its tickets and its share of the spending in percent. */
public record SpendingEventCategoryResponse(String name, BigDecimal spent, int ticketCount, BigDecimal sharePercent) {
}
