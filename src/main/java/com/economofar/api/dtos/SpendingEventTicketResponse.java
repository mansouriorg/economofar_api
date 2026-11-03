package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A ticket of the event. date is "yyyy-MM-dd". */
public record SpendingEventTicketResponse(String id, String merchant, String category, BigDecimal amount, String date) {
}
