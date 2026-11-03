package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * An event to create or to change. name is required (60 characters at most); icon is one of the icons
 * the app offers and "calendar" when missing; budget is above zero or missing; startDate and endDate are
 * "yyyy-MM-dd" or missing, and an end date needs a start date. archived is only read when an event is
 * changed: missing keeps what the event has.
 */
public record SpendingEventRequest(
        String name,
        String icon,
        BigDecimal budget,
        String startDate,
        String endDate,
        Boolean archived
) {
}
