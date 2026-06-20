package com.economofar.api.dtos;

public class UpdateNotificationPreferencesRequest {

    private Boolean ticketAnalysisCompleted;
    private Boolean monthlySpendingSummary;
    private Boolean unusualSpending;
    private Boolean budgetExceeded;
    private Boolean weeklyExpenseReport;

    private Boolean inAppEnabled;
    private Boolean pushEnabled;
    private Boolean emailEnabled;

    public Boolean getTicketAnalysisCompleted() {
        return ticketAnalysisCompleted;
    }

    public void setTicketAnalysisCompleted(Boolean ticketAnalysisCompleted) {
        this.ticketAnalysisCompleted = ticketAnalysisCompleted;
    }

    public Boolean getMonthlySpendingSummary() {
        return monthlySpendingSummary;
    }

    public void setMonthlySpendingSummary(Boolean monthlySpendingSummary) {
        this.monthlySpendingSummary = monthlySpendingSummary;
    }

    public Boolean getUnusualSpending() {
        return unusualSpending;
    }

    public void setUnusualSpending(Boolean unusualSpending) {
        this.unusualSpending = unusualSpending;
    }

    public Boolean getBudgetExceeded() {
        return budgetExceeded;
    }

    public void setBudgetExceeded(Boolean budgetExceeded) {
        this.budgetExceeded = budgetExceeded;
    }

    public Boolean getWeeklyExpenseReport() {
        return weeklyExpenseReport;
    }

    public void setWeeklyExpenseReport(Boolean weeklyExpenseReport) {
        this.weeklyExpenseReport = weeklyExpenseReport;
    }

    public Boolean getInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(Boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }

    public Boolean getPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(Boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public Boolean getEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(Boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }
}