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
public class TicketHistoryResponse {

    private UUID id;

    private String merchantName;

    private BigDecimal totalAmount;

    private LocalDateTime purchaseDate;

    private String category;

    private String paymentMethod;

    private ScanStatus scanStatus;

    private String imageUrl;

    /**
     * Raw OCR text.
     *
     * Used by:
     * - intelligent search
     * - export CSV
     * - export PDF if needed
     */
    private String rawOcrText;

    /**
     * Editable extracted items.
     *
     * Used by:
     * - intelligent search by product name
     * - export CSV/PDF with item details
     * - frontend advanced history if needed
     */
    private List<TicketItemResponse> items = new ArrayList<>();

    public static TicketHistoryResponse fromTicket(Ticket ticket) {
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

        return new TicketHistoryResponse(
                ticket.getId(),
                ticket.getMerchantName(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate(),
                ticket.getCategory(),
                ticket.getPaymentMethod(),
                ticket.getScanStatus(),
                ticket.getImageUrl(),
                ticket.getRawOcrText(),
                itemResponses
        );
    }
}