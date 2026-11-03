package com.economofar.api.dtos;

import java.util.List;

public record PriceTrackerResponse(
        List<PriceTrendResponse> items,
        int risingCount,
        int fallingCount,
        int stableCount
) {
}
