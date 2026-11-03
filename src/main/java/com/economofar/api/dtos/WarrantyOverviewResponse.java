package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The warranties of the saved tickets, as they stand on the day today ("2026-10-18").
 *
 * soon holds the warranties that end within soonDays days (today included), active the ones that
 * end later, expired the ones that ended in the last 12 months. olderCount counts the ones that
 * ended before that, which are not listed. A warranty date more than 50 years away is taken for a
 * typing mistake and ignored. soonCount, activeCount and expiredCount are exact; each list holds
 * 100 rows at most. protectedAmount is what the tickets of soon and active came to (amounts above
 * zero only).
 */
public record WarrantyOverviewResponse(
        String today,
        int soonDays,
        int soonCount,
        int activeCount,
        int expiredCount,
        int olderCount,
        BigDecimal protectedAmount,
        List<WarrantyOverviewItemResponse> soon,
        List<WarrantyOverviewItemResponse> active,
        List<WarrantyOverviewItemResponse> expired
) {
}
