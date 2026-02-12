package com.economofar.api.repositories;

import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Récupère tous les tickets d'un utilisateur spécifique.
     * Trié par date d'achat décroissante (du plus récent au plus ancien).
     * Utile quand vous avez l'objet User complet depuis le contexte de sécurité.
     */
    List<Ticket> findByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère tous les tickets via l'ID de l'utilisateur.
     * Hibernate va automatiquement faire la jointure sur la colonne 'user_id'.
     */
    List<Ticket> findAllByUserIdOrderByPurchaseDateDesc(UUID userId);
    
    /**
     * Optionnel : Permet de compter le nombre de tickets d'un utilisateur
     */
    long countByUserId(UUID userId);
}