package com.economofar.api.services;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;
    private final NotificationService notificationService;
    private final BudgetService budgetService;

    /**
     * Sauvegarder un ticket pour un utilisateur spécifique.
     * Mappe le DTO vers l'entité, gère la conversion de date,
     * puis déclenche les notifications dynamiques.
     */
    @Transactional
    public Ticket createTicket(TicketRequest request, User user) {

        LocalDateTime parsedDate = parsePurchaseDate(request.getPurchaseDate());

        String validImageUrl = getValidImageUrl(request.getImageUrl());

        Ticket ticket = Ticket.builder()
                .merchantName(request.getMerchantName())
                .totalAmount(request.getTotalAmount())
                .purchaseDate(parsedDate)
                .category(request.getCategory())
                .imageUrl(validImageUrl)
                .rawOcrText(request.getRawOcrText())
                .user(user)
                .build();

        Ticket savedTicket = repository.save(ticket);

        notificationService.notifyTicketAnalysisCompleted(
                user,
                savedTicket.getId(),
                savedTicket.getMerchantName(),
                toDouble(savedTicket.getTotalAmount())
        );

        checkUnusualSpending(user, savedTicket);

        budgetService.checkBudgetExceeded(user);

        return savedTicket;
    }

    /**
     * Récupérer les tickets de l'utilisateur connecté.
     */
    public List<Ticket> getMyTickets(User user) {
        return repository.findByUserOrderByPurchaseDateDesc(user);
    }

    /**
     * Détecte une dépense inhabituelle.
     *
     * Règle simple MVP :
     * - il faut au moins 5 anciens tickets récents
     * - le nouveau ticket doit être supérieur à 2x la moyenne
     * - et supérieur à la moyenne + 30€
     */
    private void checkUnusualSpending(User user, Ticket newTicket) {
        BigDecimal newAmount = newTicket.getTotalAmount();

        if (newAmount == null) {
            return;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(60);

        List<Ticket> recentTickets = repository.findByUserAndPurchaseDateAfterOrderByPurchaseDateDesc(
                user,
                since
        );

        List<Ticket> previousTickets = recentTickets.stream()
                .filter(ticket -> ticket.getId() != null)
                .filter(ticket -> !ticket.getId().equals(newTicket.getId()))
                .filter(ticket -> ticket.getTotalAmount() != null)
                .toList();

        if (previousTickets.size() < 5) {
            return;
        }

        BigDecimal totalPreviousAmount = previousTickets.stream()
                .map(Ticket::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageAmount = totalPreviousAmount.divide(
                BigDecimal.valueOf(previousTickets.size()),
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal doubleAverage = averageAmount.multiply(BigDecimal.valueOf(2));
        BigDecimal averagePlusThirty = averageAmount.add(BigDecimal.valueOf(30));

        boolean isUnusual =
                newAmount.compareTo(doubleAverage) > 0 &&
                newAmount.compareTo(averagePlusThirty) > 0;

        if (!isUnusual) {
            return;
        }

        notificationService.notifyUnusualSpending(
                user,
                newTicket.getId(),
                newAmount.doubleValue(),
                averageAmount.doubleValue()
        );
    }

    /**
     * Parse la date envoyée par Swift.
     *
     * Formats supportés :
     * - 2023-10-27T10:00:00Z
     * - 2023-10-27T10:00:00
     * - 2023-10-27
     *
     * Si le parsing échoue, on utilise LocalDateTime.now().
     */
    private LocalDateTime parsePurchaseDate(String purchaseDate) {
        if (purchaseDate == null || purchaseDate.isBlank()) {
            return LocalDateTime.now();
        }

        String value = purchaseDate.trim();

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
                    .atStartOfDay();
        } catch (Exception ignored) {
        }

        System.err.println("⚠️ Date parsing failed for input: '" + purchaseDate + "'. Using NOW().");
        return LocalDateTime.now();
    }

    /**
     * Empêche les erreurs si imageUrl est vide ou null.
     */
    private String getValidImageUrl(String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }

        return "placeholder.jpg";
    }

    /**
     * Convertit BigDecimal vers Double pour les notifications.
     */
    private Double toDouble(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.doubleValue();
    }
}