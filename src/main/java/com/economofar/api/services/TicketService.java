package com.economofar.api.services;

import com.economofar.api.dtos.TicketEditableRequest;
import com.economofar.api.dtos.TicketEditableResponse;
import com.economofar.api.dtos.TicketItemRequest;
import com.economofar.api.dtos.TicketItemResponse;
import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;
    private final NotificationService notificationService;
    private final BudgetService budgetService;
    private final TicketScanStatusService ticketScanStatusService;

    /**
     * Saves the validated ticket after the user reviews the OCR result.
     *
     * This now supports editable extracted data:
     * - merchant
     * - date
     * - total
     * - category
     * - editable items
     */
    @Transactional
    public Ticket createTicket(TicketRequest request, User user) {
        if (request == null) {
            throw new IllegalArgumentException("Ticket request is required");
        }

        if (user == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        LocalDateTime parsedDate = parsePurchaseDate(request.getPurchaseDate());
        String validImageUrl = getValidImageUrl(request.getImageUrl());
        String merchantName = getValidMerchantName(request.getMerchantName());

        List<TicketItem> extractedItems = buildItems(request.getItems());

        BigDecimal totalAmount = request.getTotalAmount();

        if (totalAmount == null && !extractedItems.isEmpty()) {
            totalAmount = computeItemsTotal(extractedItems);
        }

        Ticket ticket = Ticket.builder()
                .merchantName(merchantName)
                .totalAmount(totalAmount)
                .purchaseDate(parsedDate)
                .category(request.getCategory())
                .imageUrl(validImageUrl)
                .rawOcrText(request.getRawOcrText())
                .user(user)
                .build();

        for (TicketItem item : extractedItems) {
            ticket.addItem(item);
        }

        Ticket savedTicket = repository.save(ticket);

        savedTicket = ticketScanStatusService.markSaved(savedTicket);

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
    @Transactional(readOnly = true)
    public List<Ticket> getMyTickets(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        return repository.findByUserOrderByPurchaseDateDesc(user);
    }

    /**
     * Get one ticket with editable extracted data.
     *
     * Used by frontend before opening the edit/validation screen.
     */
    @Transactional(readOnly = true)
    public TicketEditableResponse getEditableTicket(UUID ticketId, User user) {
        Ticket ticket = getTicketForUser(ticketId, user);

        return TicketEditableResponse.fromTicket(ticket);
    }

    /**
     * Update the full editable ticket:
     * - edit merchant
     * - edit date
     * - edit total
     * - change category
     * - replace/edit all items
     */
    @Transactional
    public TicketEditableResponse updateEditableTicket(
            UUID ticketId,
            TicketEditableRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Editable ticket request is required"
            );
        }

        Ticket ticket = getTicketForUser(ticketId, user);

        if (request.getMerchantName() != null) {
            ticket.setMerchantName(getValidMerchantName(request.getMerchantName()));
        }

        if (request.getPurchaseDate() != null) {
            ticket.setPurchaseDate(parsePurchaseDate(request.getPurchaseDate()));
        }

        if (request.getCategory() != null) {
            ticket.setCategory(request.getCategory());
        }

        if (request.getRawOcrText() != null) {
            ticket.setRawOcrText(request.getRawOcrText());
        }

        if (request.getItems() != null) {
            List<TicketItem> newItems = buildItems(request.getItems());
            ticket.replaceItems(newItems);
        }

        if (request.getTotalAmount() != null) {
            ticket.setTotalAmount(request.getTotalAmount());
        } else if (ticket.getItems() != null && !ticket.getItems().isEmpty()) {
            ticket.setTotalAmount(computeItemsTotal(ticket.getItems()));
        }

        Ticket savedTicket = repository.save(ticket);

        return TicketEditableResponse.fromTicket(savedTicket);
    }

    /**
     * Add a missing item to an existing ticket.
     */
    @Transactional
    public TicketItemResponse addItem(
            UUID ticketId,
            TicketItemRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item request is required"
            );
        }

        Ticket ticket = getTicketForUser(ticketId, user);

        TicketItem item = buildItem(request);

        ticket.addItem(item);
        ticket.setTotalAmount(computeItemsTotal(ticket.getItems()));

        repository.save(ticket);

        return TicketItemResponse.fromItem(item);
    }

    /**
     * Edit one item:
     * - name
     * - quantity
     * - unit price
     * - total price
     * - category
     */
    @Transactional
    public TicketItemResponse updateItem(
            UUID ticketId,
            UUID itemId,
            TicketItemRequest request,
            User user
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item request is required"
            );
        }

        Ticket ticket = getTicketForUser(ticketId, user);

        TicketItem item = findItemInTicket(ticket, itemId);

        applyItemRequest(item, request);

        ticket.setTotalAmount(computeItemsTotal(ticket.getItems()));

        repository.save(ticket);

        return TicketItemResponse.fromItem(item);
    }

    /**
     * Delete a wrong item from a ticket.
     */
    @Transactional
    public void deleteItem(
            UUID ticketId,
            UUID itemId,
            User user
    ) {
        Ticket ticket = getTicketForUser(ticketId, user);

        TicketItem item = findItemInTicket(ticket, itemId);

        ticket.removeItem(item);
        ticket.setTotalAmount(computeItemsTotal(ticket.getItems()));

        repository.save(ticket);
    }

    /**
     * Change only the main ticket category.
     */
    @Transactional
    public TicketEditableResponse changeTicketCategory(
            UUID ticketId,
            String category,
            User user
    ) {
        Ticket ticket = getTicketForUser(ticketId, user);

        ticket.setCategory(category);

        Ticket savedTicket = repository.save(ticket);

        return TicketEditableResponse.fromTicket(savedTicket);
    }

    /**
     * Secure helper:
     * makes sure the ticket belongs to the authenticated user.
     */
    private Ticket getTicketForUser(UUID ticketId, User user) {
        if (ticketId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticket id is required"
            );
        }

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        return repository
                .findByIdAndUser(ticketId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found"
                ));
    }

    /**
     * Finds an item inside a ticket.
     * This prevents users from editing/deleting items from tickets they do not own.
     */
    private TicketItem findItemInTicket(Ticket ticket, UUID itemId) {
        if (itemId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item id is required"
            );
        }

        if (ticket.getItems() == null || ticket.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Item not found"
            );
        }

        return ticket.getItems()
                .stream()
                .filter(item -> item.getId() != null)
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Item not found"
                ));
    }

    /**
     * Converts frontend item requests into TicketItem entities.
     */
    private List<TicketItem> buildItems(List<TicketItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            return new ArrayList<>();
        }

        return itemRequests
                .stream()
                .filter(Objects::nonNull)
                .map(this::buildItem)
                .toList();
    }

    /**
     * Converts one item request into one TicketItem entity.
     */
    private TicketItem buildItem(TicketItemRequest request) {
        TicketItem item = new TicketItem();
        applyItemRequest(item, request);
        return item;
    }

    /**
     * Applies editable item fields.
     */
    private void applyItemRequest(TicketItem item, TicketItemRequest request) {
        if (item == null || request == null) {
            return;
        }

        String name = request.getName();

        item.setName(
                name != null && !name.isBlank()
                        ? name.trim()
                        : "Article"
        );

        Integer quantity = request.getEffectiveQuantity();

        item.setQuantity(
                quantity != null && quantity > 0
                        ? quantity
                        : 1
        );

        BigDecimal unitPrice = request.getEffectiveUnitPrice();
        BigDecimal totalPrice = request.getEffectiveTotalPrice();

        item.setUnitPrice(unitPrice);
        item.setTotalPrice(totalPrice);
        item.setCategory(request.getCategory());
    }

    /**
     * Computes the total from all item total prices.
     */
    private BigDecimal computeItemsTotal(List<TicketItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return items
                .stream()
                .map(TicketItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        if (user == null || newTicket == null) {
            return;
        }

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
     * Empêche l'erreur SQL si merchantName est vide alors que la colonne est nullable = false.
     */
    private String getValidMerchantName(String merchantName) {
        if (merchantName != null && !merchantName.isBlank()) {
            return merchantName.trim();
        }

        return "Commerçant inconnu";
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