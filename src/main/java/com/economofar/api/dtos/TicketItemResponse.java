package com.economofar.api.dtos;

import com.economofar.api.models.TicketItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketItemResponse {

    private UUID id;

    private String name;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalPrice;

    private String category;

    public static TicketItemResponse fromItem(TicketItem item) {
        if (item == null) {
            return null;
        }

        return new TicketItemResponse(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getCategory()
        );
    }
}