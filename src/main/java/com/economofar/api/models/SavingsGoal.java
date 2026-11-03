package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A savings goal: a name, an amount to reach and an optional date.
 * The money put aside is the sum of its contributions.
 */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    private LocalDate deadline;

    @Column(length = 40)
    private String icon;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavingsContribution> contributions = new ArrayList<>();

    public SavingsGoal() {
    }

    public SavingsGoal(User user, String name, BigDecimal targetAmount, LocalDate deadline, String icon) {
        this.user = user;
        this.name = name;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
        this.icon = icon;
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

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public String getIcon() {
        return icon;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @JsonIgnore
    public List<SavingsContribution> getContributions() {
        return contributions;
    }

    public void update(String name, BigDecimal targetAmount, LocalDate deadline, String icon) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
        this.icon = icon;
        this.updatedAt = LocalDateTime.now();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
