package com.economofar.api.dtos;

import com.economofar.api.models.Ticket;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class DashboardRecentTicketResponse {

    private UUID id;
    private String merchantName;
    private BigDecimal totalAmount;
    private LocalDateTime purchaseDate;
    private String category;
    private String imageUrl;

    public DashboardRecentTicketResponse() {
    }

    public DashboardRecentTicketResponse(
            UUID id,
            String merchantName,
            BigDecimal totalAmount,
            LocalDateTime purchaseDate,
            String category,
            String imageUrl
    ) {
        this.id = id;
        this.merchantName = merchantName;
        this.totalAmount = totalAmount;
        this.purchaseDate = purchaseDate;
        this.category = category;
        this.imageUrl = imageUrl;
    }

    public static DashboardRecentTicketResponse fromTicket(Ticket ticket) {
        if (ticket == null) {
            return new DashboardRecentTicketResponse();
        }

        return new DashboardRecentTicketResponse(
                ticket.getId(),
                ticket.getMerchantName(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate(),
                ticket.getCategory(),
                ticket.getImageUrl()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}