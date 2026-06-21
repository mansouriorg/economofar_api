package com.economofar.api.repositories;

import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Récupère tous les tickets d'un utilisateur spécifique.
     * Trié par date d'achat décroissante, du plus récent au plus ancien.
     * Utile quand vous avez l'objet User complet depuis le contexte de sécurité.
     */
    List<Ticket> findByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère tous les tickets via l'ID de l'utilisateur.
     * Hibernate va automatiquement faire la jointure sur la colonne user_id.
     */
    List<Ticket> findAllByUserIdOrderByPurchaseDateDesc(UUID userId);

    /**
     * Compte le nombre total de tickets d'un utilisateur.
     */
    long countByUserId(UUID userId);

    /**
     * Récupère les 5 tickets les plus récents d'un utilisateur.
     * Utilisé pour afficher les tickets récents dans le dashboard.
     */
    List<Ticket> findTop5ByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère le ticket le plus récent d'un utilisateur.
     * Utilisé pour la smart card "Last scan status".
     */
    Ticket findTopByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère les tickets d'un utilisateur entre deux dates d'achat.
     * Utilisé pour les résumés hebdomadaires, mensuels, le budget et les dépenses inhabituelles.
     */
    List<Ticket> findByUserAndPurchaseDateBetween(
            User user,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Même recherche, mais directement avec l'ID de l'utilisateur.
     * Utile si vous avez seulement l'UUID de l'utilisateur.
     */
    List<Ticket> findAllByUserIdAndPurchaseDateBetween(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Récupère les tickets récents d'un utilisateur depuis une certaine date.
     * Utile pour calculer une moyenne récente et détecter les dépenses inhabituelles.
     */
    List<Ticket> findByUserAndPurchaseDateAfterOrderByPurchaseDateDesc(
            User user,
            LocalDateTime since
    );

    /**
     * Récupère les tickets récents d'un utilisateur depuis une certaine date via son ID.
     */
    List<Ticket> findAllByUserIdAndPurchaseDateAfterOrderByPurchaseDateDesc(
            UUID userId,
            LocalDateTime since
    );
}