package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The price of the articles of the saved tickets over the last months. An article is followed when it
 * was bought on two different days or in two named shops. ticketCount is the number of tickets with at
 * least one priced article, itemCount the number of different articles, trackedCount those that are
 * followed, of which risingCount, fallingCount and steadyCount have a trend and compareOnlyCount were
 * bought on a single day only; onceCount are the articles that are not followed.
 *
 * basketChangePercent is the change of the prices of the followed articles that have a trend, each
 * weighted by what was spent on it (one decimal, null when there is none). potentialSaving adds the
 * potential saving of the articles bought in several shops, savingItemCount is the number of articles
 * where it is above zero.
 *
 * items holds the 100 followed articles on which the most was spent; hiddenCount are the others.
 */
public record ItemPricesResponse(
        int months,
        String from,
        String to,
        int ticketCount,
        int itemCount,
        int trackedCount,
        int risingCount,
        int fallingCount,
        int steadyCount,
        int compareOnlyCount,
        int onceCount,
        BigDecimal basketChangePercent,
        BigDecimal potentialSaving,
        int savingItemCount,
        int hiddenCount,
        List<ItemPriceResponse> items
) {
}
