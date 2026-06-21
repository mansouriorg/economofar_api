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

    public static DashboardRecentTicketResponse fromTicket(Ticket ticket) {
        DashboardRecentTicketResponse response = new DashboardRecentTicketResponse();

        response.id = ticket.getId();
        response.merchantName = ticket.getMerchantName();
        response.totalAmount = ticket.getTotalAmount();
        response.purchaseDate = ticket.getPurchaseDate();
        response.category = ticket.getCategory();
        response.imageUrl = ticket.getImageUrl();

        return response;
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