package com.economofar.api.dtos;

import java.util.List;

/**
 * An event with what makes its spending. categories holds 30 at most (categoryTotal is how many there
 * are), merchants the 5 where most was spent (merchantTotal is how many there are), days the latest 60 days
 * with spending, oldest first, and tickets the latest 100, newest first.
 */
public record SpendingEventDetailResponse(
        SpendingEventResponse event,
        int categoryTotal,
        List<SpendingEventCategoryResponse> categories,
        int merchantTotal,
        List<SpendingEventMerchantResponse> merchants,
        int hiddenDayCount,
        List<SpendingEventDayResponse> days,
        int hiddenTicketCount,
        List<SpendingEventTicketResponse> tickets
) {
}
