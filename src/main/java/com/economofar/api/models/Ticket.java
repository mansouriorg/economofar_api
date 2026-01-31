package com.economofar.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // L'URL de l'image (stockée plus tard sur un cloud ou dossier local)
    private String imageUrl;

    private String merchantName; // Nom du magasin (ex: Carrefour)
    
    private BigDecimal totalAmount; // Montant (ex: 12.50)

    private LocalDateTime purchaseDate; // Date de l'achat

    private String category; // Alimentation, Transport, etc.

    @Column(columnDefinition = "TEXT")
    private String rawOcrText; // Le texte brut extrait par l'IA (pour debug)

    // Un ticket appartient à UN utilisateur
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}