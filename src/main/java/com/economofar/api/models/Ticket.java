package com.economofar.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "tickets") // "tickets" est souvent préférable car "ticket" peut être un mot réservé
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // L'URL de l'image (stockée plus tard sur un cloud ou dossier local)
    private String imageUrl;

    @Column(nullable = false)
    private String merchantName; // Nom du magasin (ex: Carrefour)
    
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount; // Montant avec précision monétaire

    private LocalDateTime purchaseDate; // Date de l'achat

    private String category; // Alimentation, Transport, etc.

    @Column(columnDefinition = "TEXT")
    private String rawOcrText; // Le texte brut extrait par l'IA (pour debug)

    /**
     * Un ticket appartient à UN utilisateur.
     * @JsonIgnore est CRUCIAL ici : cela empêche Spring de charger l'utilisateur entier 
     * à chaque fois que tu récupères un ticket (évite la boucle infinie JSON).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore 
    private User user;

    /**
     * Méthode d'aide pour s'assurer que la date de création 
     * est remplie si l'IA ne la trouve pas.
     */
    @PrePersist
    protected void onCreate() {
        if (purchaseDate == null) {
            purchaseDate = LocalDateTime.now();
        }
    }
}