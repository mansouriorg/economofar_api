package com.economofar.api.dtos;

/**
 * One part of the financial health score. score is 0 to 100, or null when it cannot be
 * computed yet; detail then says what is missing. tip is a short advice when the score is low.
 */
public record HealthComponentResponse(
        String key,
        String title,
        Integer score,
        int weight,
        String detail,
        String tip
) {
}
