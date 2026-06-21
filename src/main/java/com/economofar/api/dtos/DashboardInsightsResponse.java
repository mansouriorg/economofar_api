package com.economofar.api.dtos;

import java.math.BigDecimal;
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

    private List<DashboardRecentTicketResponse> recentTickets;

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

    public List<DashboardRecentTicketResponse> getRecentTickets() {
        return recentTickets;
    }
}