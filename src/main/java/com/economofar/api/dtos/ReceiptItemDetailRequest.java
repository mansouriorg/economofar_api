package com.economofar.api.dtos;

import com.economofar.api.models.ReceiptUsageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemDetailRequest {

    private String name;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalPrice;

    private String category;

    private ReceiptUsageType usageType;
}