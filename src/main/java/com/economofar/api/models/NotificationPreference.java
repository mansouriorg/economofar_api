package com.economofar.api.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean ticketAnalysisCompleted = true;

    @Column(nullable = false)
    private boolean monthlySpendingSummary = true;

    @Column(nullable = false)
    private boolean unusualSpending = true;

    @Column(nullable = false)
    private boolean budgetExceeded = true;

    @Column(nullable = false)
    private boolean weeklyExpenseReport = true;

    @Column(nullable = false)
    private boolean inAppEnabled = true;

    @Column(nullable = false)
    private boolean pushEnabled = false;

    @Column(nullable = false)
    private boolean emailEnabled = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public NotificationPreference() {
    }

    public NotificationPreference(User user) {
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public boolean isTicketAnalysisCompleted() {
        return ticketAnalysisCompleted;
    }

    public void setTicketAnalysisCompleted(boolean ticketAnalysisCompleted) {
        this.ticketAnalysisCompleted = ticketAnalysisCompleted;
    }

    public boolean isMonthlySpendingSummary() {
        return monthlySpendingSummary;
    }

    public void setMonthlySpendingSummary(boolean monthlySpendingSummary) {
        this.monthlySpendingSummary = monthlySpendingSummary;
    }

    public boolean isUnusualSpending() {
        return unusualSpending;
    }

    public void setUnusualSpending(boolean unusualSpending) {
        this.unusualSpending = unusualSpending;
    }

    public boolean isBudgetExceeded() {
        return budgetExceeded;
    }

    public void setBudgetExceeded(boolean budgetExceeded) {
        this.budgetExceeded = budgetExceeded;
    }

    public boolean isWeeklyExpenseReport() {
        return weeklyExpenseReport;
    }

    public void setWeeklyExpenseReport(boolean weeklyExpenseReport) {
        this.weeklyExpenseReport = weeklyExpenseReport;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}