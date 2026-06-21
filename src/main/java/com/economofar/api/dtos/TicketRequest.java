package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketRequest {

    private String merchantName;

    /**
     * BigDecimal is better for money.
     * Jackson can convert the numeric value sent by Swift into BigDecimal.
     */
    private BigDecimal totalAmount;

    /**
     * Date received from Swift as String.
     * Example:
     * 2026-06-21T10:30:00Z
     *
     * It will be parsed into LocalDateTime inside TicketService.
     */
    private String purchaseDate;

    /**
     * Main ticket category:
     * Alimentation, Transport, Santé, Shopping, etc.
     */
    private String category;

    /**
     * Payment method:
     * Carte, Espèces, Chèque, Virement, Apple Pay, etc.
     *
     * Used by:
     * - ticket saving
     * - editable ticket data
     * - history filters
     */
    private String paymentMethod;

    /**
     * Image URL stored locally or on cloud storage.
     */
    private String imageUrl;

    /**
     * Raw OCR or AI result.
     */
    private String rawOcrText;

    /**
     * Editable extracted items.
     *
     * This supports:
     * - edit each item
     * - add missing item
     * - delete wrong item
     * - change quantity
     * - change item category
     */
    private List<TicketItemRequest> items;
}