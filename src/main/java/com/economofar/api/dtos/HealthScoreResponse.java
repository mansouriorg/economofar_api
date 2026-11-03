package com.economofar.api.dtos;

import java.util.List;

/**
 * The financial health score of the user. score is 0 to 100 and is null while fewer than two
 * components can be computed. level is "excellent", "good", "watch", "fragile" or "none".
 * tips holds up to three pieces of advice, the most useful first.
 */
public record HealthScoreResponse(
        Integer score,
        String level,
        int availableComponents,
        List<HealthComponentResponse> components,
        List<String> tips
) {
}
