package com.economofar.api.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardInsightsResponse {

    private BigDecimal totalScannedAmount;
    private long ticketCount;
    private BigDecimal averageBasket;

    private BigDecimal totalSpentThisWeek;
    private BigDecimal totalSpentThisMonth;
    private BigDecimal totalSpentLastMonth;
    private Double monthlyComparisonPercentage;

    private BigDecimal highestReceiptAmount;
    private String highestReceiptMerchant;

    private BigDecimal averageTicketAmount;
    private String mostFrequentMerchant;

    /**
     * Smart cards fields.
     */
    private BigDecimal monthlyBudget;
    private BigDecimal budgetRemaining;
    private boolean budgetExceeded;

    private String lastScanStatus;
    private String lastScanMerchant;
    private BigDecimal lastScanAmount;
    private LocalDateTime lastScanDate;

    private List<DashboardRecentTicketResponse> recentTickets;

    public DashboardInsightsResponse() {
    }

    public DashboardInsightsResponse(
            BigDecimal totalScannedAmount,
            long ticketCount,
            BigDecimal averageBasket,
            BigDecimal totalSpentThisWeek,
            BigDecimal totalSpentThisMonth,
            BigDecimal totalSpentLastMonth,
            Double monthlyComparisonPercentage,
            BigDecimal highestReceiptAmount,
            String highestReceiptMerchant,
            BigDecimal averageTicketAmount,
            String mostFrequentMerchant,
            BigDecimal monthlyBudget,
            BigDecimal budgetRemaining,
            boolean budgetExceeded,
            String lastScanStatus,
            String lastScanMerchant,
            BigDecimal lastScanAmount,
            LocalDateTime lastScanDate,
            List<DashboardRecentTicketResponse> recentTickets
    ) {
        this.totalScannedAmount = totalScannedAmount;
        this.ticketCount = ticketCount;
        this.averageBasket = averageBasket;
        this.totalSpentThisWeek = totalSpentThisWeek;
        this.totalSpentThisMonth = totalSpentThisMonth;
        this.totalSpentLastMonth = totalSpentLastMonth;
        this.monthlyComparisonPercentage = monthlyComparisonPercentage;
        this.highestReceiptAmount = highestReceiptAmount;
        this.highestReceiptMerchant = highestReceiptMerchant;
        this.averageTicketAmount = averageTicketAmount;
        this.mostFrequentMerchant = mostFrequentMerchant;
        this.monthlyBudget = monthlyBudget;
        this.budgetRemaining = budgetRemaining;
        this.budgetExceeded = budgetExceeded;
        this.lastScanStatus = lastScanStatus;
        this.lastScanMerchant = lastScanMerchant;
        this.lastScanAmount = lastScanAmount;
        this.lastScanDate = lastScanDate;
        this.recentTickets = recentTickets;
    }

    public BigDecimal getTotalScannedAmount() {
        return totalScannedAmount;
    }

    public long getTicketCount() {
        return ticketCount;
    }

    public BigDecimal getAverageBasket() {
        return averageBasket;
    }

    public BigDecimal getTotalSpentThisWeek() {
        return totalSpentThisWeek;
    }

    public BigDecimal getTotalSpentThisMonth() {
        return totalSpentThisMonth;
    }

    public BigDecimal getTotalSpentLastMonth() {
        return totalSpentLastMonth;
    }

    public Double getMonthlyComparisonPercentage() {
        return monthlyComparisonPercentage;
    }

    public BigDecimal getHighestReceiptAmount() {
        return highestReceiptAmount;
    }

    public String getHighestReceiptMerchant() {
        return highestReceiptMerchant;
    }

    public BigDecimal getAverageTicketAmount() {
        return averageTicketAmount;
    }

    public String getMostFrequentMerchant() {
        return mostFrequentMerchant;
    }

    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    public BigDecimal getBudgetRemaining() {
        return budgetRemaining;
    }

    public boolean isBudgetExceeded() {
        return budgetExceeded;
    }

    public String getLastScanStatus() {
        return lastScanStatus;
    }

    public String getLastScanMerchant() {
        return lastScanMerchant;
    }

    public BigDecimal getLastScanAmount() {
        return lastScanAmount;
    }

    public LocalDateTime getLastScanDate() {
        return lastScanDate;
    }

    public List<DashboardRecentTicketResponse> getRecentTickets() {
        return recentTickets;
    }
}