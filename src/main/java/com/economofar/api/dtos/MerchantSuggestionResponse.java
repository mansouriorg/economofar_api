package com.economofar.api.dtos;

/**
 * What the app should pre-fill for a shop, based on the tickets the user already saved there.
 * category and paymentMethod are null when there is no history for this shop.
 */
public record MerchantSuggestionResponse(
        String merchantName,
        String category,
        String paymentMethod,
        int ticketCount
) {
}
