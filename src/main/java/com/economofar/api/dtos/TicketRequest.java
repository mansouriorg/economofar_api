package com.economofar.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketRequest {
    private String merchantName;
    private BigDecimal totalAmount;
    private LocalDateTime purchaseDate;
    private String category;
    private String imageUrl; 
    
    // ✅ Ajouté pour correspondre au modèle Ticket et au Service
    private String rawOcrText; 
}