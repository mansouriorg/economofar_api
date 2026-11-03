package com.economofar.api.dtos;

import java.math.BigDecimal;

/** A ticket that can be put in an event. inWindow tells that its date is within the dates of the event. */
public record SpendingEventCandidateResponse(String id, String merchant, String category, BigDecimal amount, String date, boolean inWindow) {
}
