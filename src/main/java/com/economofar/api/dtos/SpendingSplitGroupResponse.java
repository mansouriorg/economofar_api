package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One slice of the split. code is "NEED", "WANT", "SAVINGS" or "UNSORTED".
 *
 * targetPercent is the share of the income that the 50/30/20 rule gives to the slice (50.0, 30.0 and
 * 20.0), null for "UNSORTED". amount is what was spent in the slice, and for "SAVINGS" what is left of
 * the income after the spending (negative when more was spent than earned), null when the month has no
 * income. percentOfIncome is amount in percent of the income, with one decimal, null without income;
 * it never crosses the target when the status says the other thing. targetAmount is the target applied
 * to the income and difference is amount minus targetAmount; both are null without income and for
 * "UNSORTED".
 *
 * status is "OK" (a need or a want that is within its target, or savings that reach theirs), "OVER" (a
 * need or a want above its target), "UNDER" (savings below their target) or "NONE" (no target, or no
 * income to compare with).
 *
 * share is the part of the slice in the spending that counts, in percent with one decimal, for the
 * needs, the wants and the unsorted; null when nothing was spent and for the savings. categoryCount is
 * the number of categories that are in the slice.
 */
public record SpendingSplitGroupResponse(
        String code,
        BigDecimal targetPercent,
        BigDecimal amount,
        BigDecimal percentOfIncome,
        BigDecimal targetAmount,
        BigDecimal difference,
        String status,
        BigDecimal share,
        int categoryCount
) {
}
