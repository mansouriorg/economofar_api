package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A shop of the event: what was spent there and its tickets. */
public record SpendingEventMerchantResponse(String name, BigDecimal spent, int ticketCount) {
}
