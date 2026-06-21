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
     */
    private String imageUrl;

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
     * Ticket category: Alimentation, Transport, Santé, etc.
     */
    private String category;

    /**
     * Raw OCR or AI result.
     */
    @Column(columnDefinition = "TEXT")
    private String rawOcrText;

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
    }
}