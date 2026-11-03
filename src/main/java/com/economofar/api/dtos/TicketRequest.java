package com.economofar.api.dtos;

import com.economofar.api.models.ReceiptUsageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketRequest {

    /**
     * Id of the ticket created by POST /upload.
     * When present, POST /scan finalizes that ticket (same id, same photo)
     * instead of creating a second one.
     */
    private UUID ticketId;

    private String merchantName;

    /**
     * BigDecimal is better for money.
     * Jackson can convert the numeric value sent by Swift into BigDecimal.
     */
    private BigDecimal totalAmount;

    /**
     * Date received from Swift as String.
     *
     * Examples:
     * 2026-06-21T10:30:00Z
     * 2026-06-21T10:30:00
     * 2026-06-21
     *
     * It will be parsed into LocalDateTime inside TicketService.
     */
    private String purchaseDate;

    /**
     * Main ticket category:
     * Alimentation, Transport, Santé, Shopping, Restaurant, etc.
     */
    private String category;

    /**
     * Payment method:
     * Carte, Espèces, Chèque, Virement, Apple Pay, Google Pay, etc.
     *
     * Used by:
     * - ticket saving
     * - editable ticket data
     * - history filters
     * - export CSV/PDF
     */
    private String paymentMethod;

    /**
     * Image URL stored locally or on cloud storage.
     *
     * Used by receipt detail screen to show the original receipt image.
     */
    private String imageUrl;

    /**
     * Raw OCR or AI result.
     *
     * Used by:
     * - receipt detail
     * - intelligent search
     * - CSV/PDF exports
     */
    private String rawOcrText;

    /**
     * User note for this receipt.
     *
     * Example:
     * "To reimburse by company"
     * "Warranty receipt for laptop"
     */
    private String note;

    /**
     * Warranty date.
     *
     * Expected format:
     * yyyy-MM-dd
     */
    private String warrantyDate;

    /**
     * Whether this receipt can be reimbursed.
     */
    private Boolean reimbursable;

    /**
     * Personal or business receipt.
     *
     * Values:
     * PERSONAL
     * BUSINESS
     */
    private ReceiptUsageType usageType;

    /**
     * VAT amount for business/accounting use.
     */
    private BigDecimal vatAmount;

    /**
     * Whether this receipt is deductible.
     */
    private Boolean deductible;

    /**
     * Project name for business receipts.
     */
    private String projectName;

    /**
     * Client name for business receipts.
     */
    private String clientName;

    /**
     * Expense report reference.
     *
     * Example:
     * EXP-2026-06-001
     */
    private String expenseReportReference;

    /**
     * Editable extracted items.
     *
     * This supports:
     * - edit each item
     * - add missing item
     * - delete wrong item
     * - change quantity
     * - change item category
     * - mark item as PERSONAL or BUSINESS
     */
    private List<TicketItemRequest> items;
}