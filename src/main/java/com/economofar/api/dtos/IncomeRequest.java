package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Creates or replaces an income. date and endDate are ISO dates (yyyy-MM-dd). date is
 * the day of the income, or the first day of a recurring one (today when missing).
 * endDate only makes sense for a recurring income.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeRequest {

    private String name;

    private BigDecimal amount;

    private String date;

    private Boolean recurring;

    private String endDate;
}
