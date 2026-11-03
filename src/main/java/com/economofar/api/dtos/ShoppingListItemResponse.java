package com.economofar.api.dtos;

import java.math.BigDecimal;

/**
 * One line of the shopping list.
 *
 * unitPrice and lineTotal are null when no price is known for the item. The price comes from the
 * saved tickets (the item named matchedName): unitPrice is the last price paid.
 * cheaperMerchant and cheaperPrice are filled only when another shop recently sold it cheaper.
 */
public record ShoppingListItemResponse(
        Long id,
        String name,
        int quantity,
        boolean checked,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String lastMerchant,
        String cheaperMerchant,
        BigDecimal cheaperPrice,
        String matchedName
) {
}
