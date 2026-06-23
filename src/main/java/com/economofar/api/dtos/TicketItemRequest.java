package com.economofar.api.dtos;

import com.economofar.api.models.ReceiptUsageType;
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

    /**
     * Personal or business item.
     *
     * Values:
     * PERSONAL
     * BUSINESS
     */
    private ReceiptUsageType usageType;

    public Integer getEffectiveQuantity() {
        if (quantity != null && quantity > 0) {
            return quantity;
        }

        if (qty != null && qty > 0) {
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

        return effectiveUnitPrice.multiply(
                BigDecimal.valueOf(getEffectiveQuantity())
        );
    }

    public ReceiptUsageType getEffectiveUsageType() {
        if (usageType != null) {
            return usageType;
        }

        return ReceiptUsageType.PERSONAL;
    }
}