package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * What the tickets of a category came to at one merchant. ticketCount is the number of tickets of the
 * merchant in the category and amount counts only the values above zero. The tickets without a known
 * merchant are one entry named "Enseigne inconnue".
 */
public record CategoryMerchantResponse(
        String name,
        int ticketCount,
        BigDecimal amount
) {
}
