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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ticket_items")
public class TicketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Product/item name.
     *
     * Example:
     * Milk
     * Coffee
     * Bread
     */
    @Column(nullable = false)
    private String name;

    /**
     * Item quantity.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Unit price.
     */
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Total price for this item.
     *
     * If missing, it can be calculated from:
     * unitPrice x quantity
     */
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Item category.
     *
     * Example:
     * Groceries
     * Restaurant
     * Transport
     * Health
     * Office supplies
     */
    @Column(name = "category")
    private String category;

    /**
     * Personal or business item.
     *
     * This allows the receipt detail screen to mark each product as:
     * - PERSONAL
     * - BUSINESS
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 30)
    private ReceiptUsageType usageType = ReceiptUsageType.PERSONAL;

    /**
     * Creation date.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Last update date.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Parent ticket.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Ticket ticket;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (name == null || name.isBlank()) {
            name = "Article";
        } else {
            name = name.trim();
        }

        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }

        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            unitPrice = BigDecimal.ZERO;
        }

        if (totalPrice == null) {
            if (unitPrice != null) {
                totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            } else {
                totalPrice = BigDecimal.ZERO;
            }
        }

        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        if (category != null && category.isBlank()) {
            category = null;
        } else if (category != null) {
            category = category.trim();
        }

        if (usageType == null) {
            usageType = ReceiptUsageType.PERSONAL;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (name == null || name.isBlank()) {
            name = "Article";
        } else {
            name = name.trim();
        }

        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }

        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            unitPrice = BigDecimal.ZERO;
        }

        if (totalPrice == null) {
            if (unitPrice != null) {
                totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            } else {
                totalPrice = BigDecimal.ZERO;
            }
        }

        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        if (category != null && category.isBlank()) {
            category = null;
        } else if (category != null) {
            category = category.trim();
        }

        if (usageType == null) {
            usageType = ReceiptUsageType.PERSONAL;
        }

        updatedAt = LocalDateTime.now();
    }
}