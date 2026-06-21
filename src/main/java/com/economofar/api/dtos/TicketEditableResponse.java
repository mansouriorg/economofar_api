package com.economofar.api.dtos;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketEditableResponse {

    private UUID id;

    private String merchantName;

    private BigDecimal totalAmount;

    private LocalDateTime purchaseDate;

    private String category;

    /**
     * Payment method:
     * Carte, Espèces, Chèque, Virement, Apple Pay, etc.
     *
     * Used by:
     * - editable ticket screen
     * - history filters
     */
    private String paymentMethod;

    private String imageUrl;

    private String rawOcrText;

    private ScanStatus scanStatus;

    private List<TicketItemResponse> items = new ArrayList<>();

    public static TicketEditableResponse fromTicket(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        List<TicketItemResponse> itemResponses = new ArrayList<>();

        if (ticket.getItems() != null) {
            itemResponses = ticket.getItems()
                    .stream()
                    .map(TicketItemResponse::fromItem)
                    .toList();
        }

        return new TicketEditableResponse(
                ticket.getId(),
                ticket.getMerchantName(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate(),
                ticket.getCategory(),
                ticket.getPaymentMethod(),
                ticket.getImageUrl(),
                ticket.getRawOcrText(),
                ticket.getScanStatus(),
                itemResponses
        );
    }
}