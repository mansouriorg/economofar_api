package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money put into a savings goal (positive amount) or taken out of it (negative amount).
 */
@Entity
@Table(name = "savings_contributions")
public class SavingsContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private SavingsGoal goal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate contributedOn;

    @Column(length = 200)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SavingsContribution() {
    }

    public SavingsContribution(SavingsGoal goal, BigDecimal amount, LocalDate contributedOn, String note) {
        this.goal = goal;
        this.amount = amount;
        this.contributedOn = contributedOn;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public SavingsGoal getGoal() {
        return goal;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getContributedOn() {
        return contributedOn;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
