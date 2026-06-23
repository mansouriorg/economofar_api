package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Image URL stored locally or on cloud storage.
     *
     * Used by the receipt detail screen to show the original uploaded image.
     */
    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Original uploaded file name.
     *
     * Example:
     * receipt_carrefour_2026_06_23.jpg
     */
    @Column(name = "original_file_name")
    private String originalFileName;

    /**
     * Image content type.
     *
     * Example:
     * image/jpeg
     * image/png
     */
    @Column(name = "image_content_type")
    private String imageContentType;

    /**
     * Merchant name.
     *
     * During the first upload step, OCR may not have detected the merchant yet,
     * so we set a safe default before persisting.
     */
    @Column(nullable = false)
    private String merchantName;

    /**
     * Total amount with monetary precision.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Purchase date.
     */
    private LocalDateTime purchaseDate;

    /**
     * Ticket category: Alimentation, Transport, Santé, Restaurant, Shopping, etc.
     */
    private String category;

    /**
     * Payment method:
     * Carte, Espèces, Chèque, Virement, Apple Pay, etc.
     *
     * Used by history filters and receipt detail.
     */
    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    /**
     * Raw OCR or AI result.
     *
     * Used by:
     * - intelligent search
     * - receipt detail
     * - export CSV/PDF
     */
    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    /**
     * Receipt note added by the user.
     *
     * Example:
     * "To reimburse by company"
     * "Warranty receipt for laptop"
     */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * Warranty date.
     *
     * Used for products with warranty.
     */
    @Column(name = "warranty_date")
    private LocalDate warrantyDate;

    /**
     * Whether this receipt can be reimbursed.
     */
    @Builder.Default
    @Column(name = "reimbursable", nullable = false)
    private Boolean reimbursable = false;

    /**
     * Personal or business receipt.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 30)
    private ReceiptUsageType usageType = ReceiptUsageType.PERSONAL;

    /**
     * VAT amount for business/accounting use.
     */
    @Column(name = "vat_amount", precision = 12, scale = 2)
    private BigDecimal vatAmount;

    /**
     * Whether this receipt is deductible.
     */
    @Builder.Default
    @Column(name = "deductible", nullable = false)
    private Boolean deductible = false;

    /**
     * Project name for business receipts.
     *
     * Example:
     * "Website redesign"
     * "Client event"
     */
    @Column(name = "project_name")
    private String projectName;

    /**
     * Client name for business receipts.
     */
    @Column(name = "client_name")
    private String clientName;

    /**
     * Expense report reference.
     *
     * Example:
     * EXP-2026-06-001
     */
    @Column(name = "expense_report_reference")
    private String expenseReportReference;

    /**
     * Scan status tracking:
     * UPLOADING -> ANALYZING -> OCR_COMPLETED -> NEEDS_REVIEW -> SAVED
     * or FAILED if something goes wrong.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(
            name = "scan_status",
            nullable = false,
            length = 30,
            columnDefinition = "varchar(30) default 'UPLOADING'"
    )
    private ScanStatus scanStatus = ScanStatus.UPLOADING;

    /**
     * Error message if OCR/upload/save fails.
     */
    @Column(name = "scan_error_message", columnDefinition = "TEXT")
    private String scanErrorMessage;

    /**
     * When the scan/upload process started.
     */
    @Column(name = "scan_started_at")
    private LocalDateTime scanStartedAt;

    /**
     * When OCR/AI analysis completed.
     */
    @Column(name = "ocr_completed_at")
    private LocalDateTime ocrCompletedAt;

    /**
     * When the user reviewed/validated the ticket.
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * When the ticket was finally saved.
     */
    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    /**
     * Editable extracted items.
     *
     * This allows:
     * - edit each item
     * - add missing item
     * - delete wrong item
     * - change quantity
     * - change item category
     * - mark item as PERSONAL or BUSINESS
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "ticket",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<TicketItem> items = new ArrayList<>();

    /**
     * A ticket belongs to one user.
     *
     * @JsonIgnore avoids infinite JSON recursion when returning tickets.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Helper to add one item to the ticket.
     */
    public void addItem(TicketItem item) {
        if (item == null) {
            return;
        }

        if (items == null) {
            items = new ArrayList<>();
        }

        item.setTicket(this);
        items.add(item);
    }

    /**
     * Helper to remove one item from the ticket.
     */
    public void removeItem(TicketItem item) {
        if (item == null || items == null) {
            return;
        }

        items.remove(item);
        item.setTicket(null);
    }

    /**
     * Helper to replace all extracted items.
     *
     * Used when the user edits the full extracted ticket data.
     */
    public void replaceItems(List<TicketItem> newItems) {
        if (items == null) {
            items = new ArrayList<>();
        }

        items.clear();

        if (newItems == null || newItems.isEmpty()) {
            return;
        }

        for (TicketItem item : newItems) {
            addItem(item);
        }
    }

    /**
     * Default values before insert.
     */
    @PrePersist
    protected void onCreate() {
        if (merchantName == null || merchantName.isBlank()) {
            merchantName = "Analyse en cours";
        }

        if (purchaseDate == null) {
            purchaseDate = LocalDateTime.now();
        }

        if (scanStatus == null) {
            scanStatus = ScanStatus.UPLOADING;
        }

        if (scanStartedAt == null) {
            scanStartedAt = LocalDateTime.now();
        }

        if (items == null) {
            items = new ArrayList<>();
        }

        if (paymentMethod != null && paymentMethod.isBlank()) {
            paymentMethod = null;
        }

        if (category != null && category.isBlank()) {
            category = null;
        }

        if (imageUrl != null && imageUrl.isBlank()) {
            imageUrl = null;
        }

        if (originalFileName != null && originalFileName.isBlank()) {
            originalFileName = null;
        }

        if (imageContentType != null && imageContentType.isBlank()) {
            imageContentType = null;
        }

        if (note != null && note.isBlank()) {
            note = null;
        }

        if (usageType == null) {
            usageType = ReceiptUsageType.PERSONAL;
        }

        if (reimbursable == null) {
            reimbursable = false;
        }

        if (deductible == null) {
            deductible = false;
        }

        if (projectName != null && projectName.isBlank()) {
            projectName = null;
        }

        if (clientName != null && clientName.isBlank()) {
            clientName = null;
        }

        if (expenseReportReference != null && expenseReportReference.isBlank()) {
            expenseReportReference = null;
        }
    }

    /**
     * Default values before update.
     */
    @PreUpdate
    protected void onUpdate() {
        if (merchantName == null || merchantName.isBlank()) {
            merchantName = "Analyse en cours";
        }

        if (scanStatus == null) {
            scanStatus = ScanStatus.UPLOADING;
        }

        if (items == null) {
            items = new ArrayList<>();
        }

        if (paymentMethod != null && paymentMethod.isBlank()) {
            paymentMethod = null;
        }

        if (category != null && category.isBlank()) {
            category = null;
        }

        if (imageUrl != null && imageUrl.isBlank()) {
            imageUrl = null;
        }

        if (originalFileName != null && originalFileName.isBlank()) {
            originalFileName = null;
        }

        if (imageContentType != null && imageContentType.isBlank()) {
            imageContentType = null;
        }

        if (note != null && note.isBlank()) {
            note = null;
        }

        if (usageType == null) {
            usageType = ReceiptUsageType.PERSONAL;
        }

        if (reimbursable == null) {
            reimbursable = false;
        }

        if (deductible == null) {
            deductible = false;
        }

        if (projectName != null && projectName.isBlank()) {
            projectName = null;
        }

        if (clientName != null && clientName.isBlank()) {
            clientName = null;
        }

        if (expenseReportReference != null && expenseReportReference.isBlank()) {
            expenseReportReference = null;
        }
    }
}