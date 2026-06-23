package com.economofar.api.dtos;

import com.economofar.api.models.ReceiptUsageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailUpdateRequest {

    private String merchantName;

    private String purchaseDate;

    private BigDecimal totalAmount;

    private String category;

    private String paymentMethod;

    private String rawOcrText;

    private String imageUrl;

    private String note;

    /**
     * Expected format:
     * yyyy-MM-dd
     */
    private String warrantyDate;

    private Boolean reimbursable;

    private ReceiptUsageType usageType;

    private BigDecimal vatAmount;

    private Boolean deductible;

    private String projectName;

    private String clientName;

    private String expenseReportReference;
}