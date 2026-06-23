package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketMonthlyReportResponse {

    private String month;

    private String monthTitle;

    private Integer receiptCount;

    private BigDecimal totalAmount;

    private BigDecimal averageAmount;

    private String topMerchant;

    private BigDecimal highestReceiptAmount;

    private List<TicketHistoryResponse> tickets = new ArrayList<>();
}