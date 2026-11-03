package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The events of the person: the ones in progress first, then the ones to come, without dates, finished,
 * and the archived ones at the end. The counts and totalSpent are about the events that are not archived.
 */
public record SpendingEventsResponse(
        int activeCount,
        int archivedCount,
        int ongoingCount,
        int overBudgetCount,
        int nearBudgetCount,
        BigDecimal totalSpent,
        List<SpendingEventResponse> events
) {
}
