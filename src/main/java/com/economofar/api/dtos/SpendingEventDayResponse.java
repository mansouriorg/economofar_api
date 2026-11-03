package com.economofar.api.dtos;

import java.math.BigDecimal;

/** What was spent on one day of the event. date is "yyyy-MM-dd". */
public record SpendingEventDayResponse(String date, BigDecimal spent, int ticketCount) {
}
