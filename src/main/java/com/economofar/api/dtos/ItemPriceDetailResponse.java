package com.economofar.api.dtos;

import java.util.List;

/**
 * Everything about one article: the figures, the shops from the cheapest to the dearest (20 at most),
 * and the price of the latest days it was bought (60 at most, the oldest first) out of pointCount days.
 */
public record ItemPriceDetailResponse(
        int months,
        ItemPriceResponse item,
        int merchantTotal,
        List<ItemPriceMerchantResponse> merchants,
        int pointCount,
        List<ItemPricePointResponse> points
) {
}
