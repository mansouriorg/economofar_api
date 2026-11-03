package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A ticket typed by hand (cash purchase, receipt that could not be read...).
 * purchaseDate is a day (yyyy-MM-dd); an empty value means today.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualTicketRequest {

    private String merchantName;

    private BigDecimal totalAmount;

    private String purchaseDate;

    private String category;

    private String paymentMethod;

    private String note;
}
