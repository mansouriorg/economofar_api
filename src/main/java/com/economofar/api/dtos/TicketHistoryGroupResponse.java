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
public class TicketHistoryGroupResponse {

    private String groupKey;

    private String groupTitle;

    private Integer ticketCount;

    private BigDecimal totalAmount;

    private List<TicketHistoryResponse> tickets = new ArrayList<>();
}