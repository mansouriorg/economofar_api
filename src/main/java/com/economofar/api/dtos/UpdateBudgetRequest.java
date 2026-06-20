package com.economofar.api.dtos;

public class UpdateBudgetRequest {

    private Double monthlyAmount;
    private String currency;

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