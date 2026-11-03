package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Money put into a goal (positive amount) or taken out of it (negative amount).
 * date is optional (yyyy-MM-dd, today by default).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingsContributionRequest {

    private BigDecimal amount;

    private String date;

    private String note;
}
