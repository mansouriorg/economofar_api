package com.economofar.api.dtos;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketHistoryResponse {

    private UUID id;

    private String merchantName;

    private BigDecimal totalAmount;

    private LocalDateTime purchaseDate;

    private String category;

    private String paymentMethod;

    private ScanStatus scanStatus;

    private String imageUrl;

    public static TicketHistoryResponse fromTicket(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        return new TicketHistoryResponse(
                ticket.getId(),
                ticket.getMerchantName(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate(),
                ticket.getCategory(),
                ticket.getPaymentMethod(),
                ticket.getScanStatus(),
                ticket.getImageUrl()
        );
    }
}