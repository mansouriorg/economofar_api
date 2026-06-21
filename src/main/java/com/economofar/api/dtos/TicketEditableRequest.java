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

    /**
     * Editable merchant name.
     */
    private String merchantName;

    /**
     * Editable purchase date.
     *
     * Received from Swift as String.
     * Examples:
     * - 2026-06-21
     * - 2026-06-21T10:30:00
     * - 2026-06-21T10:30:00Z
     */
    private String purchaseDate;

    /**
     * Editable total amount.
     */
    private BigDecimal totalAmount;

    /**
     * Editable main ticket category:
     * Alimentation, Transport, Santé, Shopping, etc.
     */
    private String category;

    /**
     * Editable payment method:
     * Carte, Espèces, Chèque, Virement, Apple Pay, etc.
     *
     * Used by history filters.
     */
    private String paymentMethod;

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