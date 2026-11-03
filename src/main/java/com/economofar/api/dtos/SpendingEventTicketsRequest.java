package com.economofar.api.dtos;

import java.util.List;

/** The ids of the tickets to put in an event, 50 at most at a time. */
public record SpendingEventTicketsRequest(List<String> ticketIds) {
}
