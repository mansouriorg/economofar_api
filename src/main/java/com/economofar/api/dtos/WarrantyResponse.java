package com.economofar.api.dtos;

/**
 * A receipt that has a warranty end date.
 * status is "active", "expiring" (30 days or less left) or "expired".
 * Dates are ISO strings (yyyy-MM-dd). note is the note written on the receipt, if any.
 */
public record WarrantyResponse(
        String ticketId,
        String merchantName,
        String note,
        String purchaseDate,
        String endDate,
        long daysLeft,
        String status
) {
}
