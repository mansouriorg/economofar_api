package com.economofar.api.dtos;

import java.util.List;

/**
 * The saved tickets that are in no event yet. Without query: the latest 180 days and the dates of the
 * event; with a query: the tickets of all dates whose shop contains it. windowed tells that the event
 * has dates, the tickets within them come first. 100 tickets at most, hiddenCount is how many more.
 */
public record SpendingEventCandidatesResponse(
        String query,
        boolean windowed,
        String from,
        String to,
        int hiddenCount,
        List<SpendingEventCandidateResponse> tickets
) {
}
