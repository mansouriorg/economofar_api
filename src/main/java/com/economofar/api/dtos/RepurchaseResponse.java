package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * The articles bought at a regular rhythm in the period (the current month and the months before it, 3 to
 * 24), the ones to buy first at the top, 100 at most (hiddenCount is how many more there are).
 *
 * followedCount is the number of articles bought on 3 different days or more, articleCount the ones among
 * them with a regular rhythm (the listed ones) and irregularCount the others. dueCount, soonCount, okCount
 * and lapsedCount split the regular articles by status. estimatedMonthly is what the regular articles that
 * are not lapsed cost in 30 days, the sum of their monthly estimates. The counts are about all the
 * articles, not only the 100 listed.
 */
public record RepurchaseResponse(
        int months,
        String from,
        String to,
        int followedCount,
        int articleCount,
        int irregularCount,
        int dueCount,
        int soonCount,
        int okCount,
        int lapsedCount,
        BigDecimal estimatedMonthly,
        int hiddenCount,
        List<RepurchaseArticleResponse> articles
) {
}
