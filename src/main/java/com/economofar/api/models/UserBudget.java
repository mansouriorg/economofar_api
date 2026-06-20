package com.economofar.api.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_budgets")
public class UserBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Double monthlyAmount;

    @Column(nullable = false)
    private String currency = "EUR";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UserBudget() {
    }

    public UserBudget(User user, Double monthlyAmount, String currency) {
        this.user = user;
        this.monthlyAmount = monthlyAmount;
        this.currency = currency;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Double getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(Double monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}