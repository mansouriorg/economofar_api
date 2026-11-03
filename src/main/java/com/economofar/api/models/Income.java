package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A source of income. A one-off income counts in the month of its date. A recurring income
 * counts every month from the month of its start date until the month of its end date
 * (for ever when there is no end date).
 */
@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private boolean recurring;

    private LocalDate endDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Income() {
    }

    public Income(User user, String name, BigDecimal amount, LocalDate startDate, boolean recurring, LocalDate endDate) {
        this.user = user;
        this.name = name;
        this.amount = amount;
        this.startDate = startDate;
        this.recurring = recurring;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, BigDecimal amount, LocalDate startDate, boolean recurring, LocalDate endDate) {
        this.name = name;
        this.amount = amount;
        this.startDate = startDate;
        this.recurring = recurring;
        this.endDate = endDate;
        this.updatedAt = LocalDateTime.now();
    }
}
