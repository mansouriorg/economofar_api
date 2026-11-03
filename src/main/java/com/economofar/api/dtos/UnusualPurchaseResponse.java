package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * A purchase far above what the user usually spends. usualAmount is the median of the earlier
 * purchases it was compared with, ratio is amount divided by the exact median (at least 3), basis says what it was
 * compared with ("merchant" for the same shop, "category" for the same category) and samples is
 * the number of earlier purchases used.
 */
public record UnusualPurchaseResponse(
        String date,
        String merchant,
        String category,
        BigDecimal amount,
        BigDecimal usualAmount,
        BigDecimal ratio,
        String basis,
        int samples
) {
}
