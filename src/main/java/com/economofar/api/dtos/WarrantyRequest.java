package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sets the end of the warranty of a receipt, either as a number of months
 * counted from the purchase date, or as an exact date (yyyy-MM-dd).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyRequest {

    private Integer months;

    private String endDate;
}
