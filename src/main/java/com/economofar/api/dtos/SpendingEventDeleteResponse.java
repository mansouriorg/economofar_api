package com.economofar.api.dtos;

/** The event that was deleted and the number of tickets it held. The tickets themselves are not touched. */
public record SpendingEventDeleteResponse(long id, int releasedTickets) {
}
