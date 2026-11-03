package com.economofar.api.repositories;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /**
     * Récupère tous les tickets d'un utilisateur spécifique.
     * Trié par date d'achat décroissante, du plus récent au plus ancien.
     *
     * EntityGraph charge aussi les items pour éviter les problèmes Lazy loading
     * quand le frontend demande les tickets avec leurs articles.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère tous les tickets via l'ID de l'utilisateur.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findAllByUserIdOrderByPurchaseDateDesc(UUID userId);

    /**
     * Compte le nombre total de tickets d'un utilisateur.
     */
    long countByUserId(UUID userId);

    /**
     * Récupère un ticket spécifique appartenant à un utilisateur.
     * Utilisé pour éviter qu'un utilisateur puisse accéder au ticket d'un autre utilisateur.
     *
     * EntityGraph charge aussi les items éditables du ticket.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Ticket> findByIdAndUser(UUID id, User user);

    /**
     * Récupère les 5 tickets les plus récents d'un utilisateur.
     * Utilisé pour afficher les tickets récents dans le dashboard.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findTop5ByUserOrderByPurchaseDateDesc(User user);

    /**
     * Récupère le ticket le plus récent d'un utilisateur.
     * Utilisé pour la smart card "Last scan status".
     */
    @EntityGraph(attributePaths = "items")
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

    /**
     * Récupère les tickets d'un utilisateur selon leur statut de scan.
     * Utile pour afficher les tickets en cours d'analyse, en échec ou en attente de validation.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findByUserAndScanStatusOrderByScanStartedAtDesc(
            User user,
            ScanStatus scanStatus
    );

    /**
     * Récupère les tickets dont le statut est dans une liste donnée.
     * Utile pour suivre plusieurs états en même temps :
     * UPLOADING, ANALYZING, OCR_COMPLETED, NEEDS_REVIEW, FAILED.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findByUserAndScanStatusInOrderByScanStartedAtDesc(
            User user,
            List<ScanStatus> scanStatuses
    );

    /**
     * Récupère le dernier ticket en cours de scan ou de validation.
     * Utile côté frontend pour restaurer un scan interrompu.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Ticket> findTopByUserAndScanStatusInOrderByScanStartedAtDesc(
            User user,
            List<ScanStatus> scanStatuses
    );

    // ------------------------------------------------------------------
    // Added by the Economofar upgrade
    // ------------------------------------------------------------------

    /**
     * Saved tickets only (history, dashboard). Unfinished scans are excluded.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findByUserAndScanStatusOrderByPurchaseDateDesc(User user, ScanStatus scanStatus);

    @EntityGraph(attributePaths = "items")
    List<Ticket> findTop5ByUserAndScanStatusOrderByPurchaseDateDesc(User user, ScanStatus scanStatus);

    @EntityGraph(attributePaths = "items")
    Ticket findTopByUserAndScanStatusOrderByPurchaseDateDesc(User user, ScanStatus scanStatus);

    /**
     * Saved tickets bought after a date, with their items, the oldest first.
     * Used to follow the price of the same item over time.
     */
    @EntityGraph(attributePaths = "items")
    List<Ticket> findByUserAndScanStatusAndPurchaseDateAfterOrderByPurchaseDateAsc(
            User user,
            ScanStatus scanStatus,
            LocalDateTime since
    );

    /**
     * Saved tickets that have a warranty end date, the soonest first.
     */
    List<Ticket> findByUserAndScanStatusAndWarrantyDateIsNotNullOrderByWarrantyDateAsc(
            User user,
            ScanStatus scanStatus
    );

    /**
     * The 300 most recent saved tickets (without their items). Used to learn
     * how the user usually files the receipts of a shop.
     */
    List<Ticket> findTop300ByUserAndScanStatusOrderByPurchaseDateDesc(User user, ScanStatus scanStatus);

    List<Ticket> findByUserAndScanStatusAndPurchaseDateBetween(
            User user,
            ScanStatus scanStatus,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Ticket> findByScanStatus(ScanStatus scanStatus);

    List<Ticket> findByScanStatusInAndScanStartedAtBefore(
            List<ScanStatus> scanStatuses,
            LocalDateTime before
    );

    /**
     * Used to know if a photo is still used by another (duplicated) receipt.
     */
    long countByImageUrl(String imageUrl);

    /**
     * Used by account deletion.
     */
    void deleteByUser(User user);
}