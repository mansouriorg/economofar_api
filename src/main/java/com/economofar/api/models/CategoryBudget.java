package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Monthly spending limit for one category (for example Alimentation, Transport).
 * There is at most one limit per user and per category.
 */
@Entity
@Table(
        name = "category_budgets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"})
)
public class CategoryBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public CategoryBudget() {
    }

    public CategoryBudget(User user, String category, BigDecimal monthlyAmount) {
        this.user = user;
        this.category = category;
        this.monthlyAmount = monthlyAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
