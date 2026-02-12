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
    
    /**
     * Note: Assure-toi que le format envoyé par Swift ou parsé du JSON d'Ollama
     * est compatible avec ISO_LOCAL_DATE_TIME (ex: 2026-02-12T16:54:00)
     */
    private LocalDateTime purchaseDate;
    
    private String category;
    
    private String imageUrl; 
    
    private String rawOcrText; 
}