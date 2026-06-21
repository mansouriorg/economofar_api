package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketEditableRequest {

    private String merchantName;

    private String purchaseDate;

    private BigDecimal totalAmount;

    private String category;

    private String rawOcrText;

    private List<TicketItemRequest> items;
}