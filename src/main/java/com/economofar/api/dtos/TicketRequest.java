package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketRequest {
    
    private String merchantName;
    
    // BigDecimal is better for money, Jackson handles the conversion from Double automatically
    private BigDecimal totalAmount;
    
    /**
     * ✅ CHANGED TO STRING
     * We receive the date as a String from Swift (ISO8601).
     * We will parse this into LocalDateTime in the TicketService.
     */
    private String purchaseDate;
    
    private String category;
    
    private String imageUrl; 
    
    private String rawOcrText; 
}