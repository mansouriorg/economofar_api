package com.economofar.api.dtos;

/**
 * How many tickets of the period have one kind of problem. type is one of UNKNOWN_MERCHANT,
 * NO_CATEGORY, DUPLICATE, MISSING_AMOUNT or FUTURE_DATE.
 */
public record QualityIssueCountResponse(
        String type,
        int count
) {
}
