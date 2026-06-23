package com.economofar.api.dtos;

import com.economofar.api.models.ReceiptUsageType;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailResponse {

    private UUID id;

    private String merchantName;

    private BigDecimal totalAmount;

    private LocalDateTime purchaseDate;

    private String category;

    private String paymentMethod;

    private ScanStatus scanStatus;

    private String imageUrl;

    private String rawOcrText;

    private String note;

    private LocalDate warrantyDate;

    private Boolean reimbursable;

    private ReceiptUsageType usageType;

    private BigDecimal vatAmount;

    private Boolean deductible;

    private String projectName;

    private String clientName;

    private String expenseReportReference;

    private String originalFileName;

    private String imageContentType;

    private List<ReceiptItemDetailResponse> items = new ArrayList<>();

    public static ReceiptDetailResponse fromTicket(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        List<ReceiptItemDetailResponse> itemResponses = new ArrayList<>();

        if (ticket.getItems() != null) {
            itemResponses = ticket.getItems()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(ReceiptItemDetailResponse::fromItem)
                    .filter(Objects::nonNull)
                    .toList();
        }

        return new ReceiptDetailResponse(
                ticket.getId(),
                ticket.getMerchantName(),
                ticket.getTotalAmount(),
                ticket.getPurchaseDate(),
                ticket.getCategory(),
                ticket.getPaymentMethod(),
                ticket.getScanStatus(),
                ticket.getImageUrl(),
                ticket.getRawOcrText(),
                ticket.getNote(),
                ticket.getWarrantyDate(),
                ticket.getReimbursable(),
                ticket.getUsageType(),
                ticket.getVatAmount(),
                ticket.getDeductible(),
                ticket.getProjectName(),
                ticket.getClientName(),
                ticket.getExpenseReportReference(),
                ticket.getOriginalFileName(),
                ticket.getImageContentType(),
                itemResponses
        );
    }
}