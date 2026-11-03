package com.economofar.api.dtos;

/** A shop where the article is bought and the number of different days it was bought there. */
public record RepurchaseShopResponse(
        String name,
        int purchaseDays
) {
}
