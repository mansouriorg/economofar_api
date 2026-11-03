package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The business tickets of the period grouped by one label (expense report, project or client).
 *
 * groupCount is the number of different labels and groups holds the 20 with most spending, the first
 * one being the one with the most spending. missingCount and missingAmount are the tickets that have
 * no such label, and missing holds 10 of them at most, those of the latest days, the most recent day
 * first.
 */
public record BusinessSectionResponse(
        int groupCount,
        List<BusinessGroupResponse> groups,
        int missingCount,
        BigDecimal missingAmount,
        List<BusinessTicketResponse> missing
) {
}
