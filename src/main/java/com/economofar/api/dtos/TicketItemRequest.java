package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketItemRequest {

    private String name;

    private Integer quantity;

    /**
     * Optional alias for frontend if it sends "qty".
     */
    private Integer qty;

    private BigDecimal unitPrice;

    /**
     * Optional alias for frontend if it sends "price".
     */
    private BigDecimal price;

    private BigDecimal totalPrice;

    private String category;

    public Integer getEffectiveQuantity() {
        if (quantity != null) {
            return quantity;
        }

        if (qty != null) {
            return qty;
        }

        return 1;
    }

    public BigDecimal getEffectiveUnitPrice() {
        if (unitPrice != null) {
            return unitPrice;
        }

        return price;
    }

    public BigDecimal getEffectiveTotalPrice() {
        if (totalPrice != null) {
            return totalPrice;
        }

        BigDecimal effectiveUnitPrice = getEffectiveUnitPrice();

        if (effectiveUnitPrice == null) {
            return BigDecimal.ZERO;
        }

        return effectiveUnitPrice.multiply(BigDecimal.valueOf(getEffectiveQuantity()));
    }
}