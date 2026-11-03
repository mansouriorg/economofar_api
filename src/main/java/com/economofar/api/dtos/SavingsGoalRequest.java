package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Creates or replaces a savings goal. deadline is an optional date (yyyy-MM-dd),
 * icon is an optional short key chosen by the app.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoalRequest {

    private String name;

    private BigDecimal targetAmount;

    private String deadline;

    private String icon;
}
