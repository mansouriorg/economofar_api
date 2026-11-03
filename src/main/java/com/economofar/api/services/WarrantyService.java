package com.economofar.api.services;

import com.economofar.api.dtos.WarrantyCandidateResponse;
import com.economofar.api.dtos.WarrantyRequest;
import com.economofar.api.dtos.WarrantyResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Warranties of the purchases.
 *
 * A warranty is the warrantyDate field that already exists on a receipt (it is also editable
 * in the receipt detail screen). The iPhone app reminds the user before it ends.
 */
@Service
@RequiredArgsConstructor
public class WarrantyService {

    private static final int MAX_MONTHS = 120;
    private static final int EXPIRING_DAYS = 30;
    private static final int KEEP_EXPIRED_DAYS = 365;
    private static final int CANDIDATE_LOOKBACK_MONTHS = 36;
    private static final int CANDIDATE_LIMIT = 60;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<WarrantyResponse> list(User user) {
        LocalDate today = LocalDate.now();

        return ticketRepository
                .findByUserAndScanStatusAndWarrantyDateIsNotNullOrderByWarrantyDateAsc(user, ScanStatus.SAVED)
                .stream()
                .filter(ticket -> ChronoUnit.DAYS.between(ticket.getWarrantyDate(), today) <= KEEP_EXPIRED_DAYS)
                .map(ticket -> toResponse(ticket, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarrantyCandidateResponse> candidates(User user) {
        LocalDate today = LocalDate.now();

        return ticketRepository
                .findByUserAndScanStatusAndPurchaseDateBetween(
                        user,
                        ScanStatus.SAVED,
                        today.minusMonths(CANDIDATE_LOOKBACK_MONTHS).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                )
                .stream()
                .filter(ticket -> ticket.getPurchaseDate() != null && ticket.getWarrantyDate() == null)
                .sorted(Comparator.comparing(Ticket::getPurchaseDate).reversed())
                .limit(CANDIDATE_LIMIT)
                .map(ticket -> new WarrantyCandidateResponse(
                        String.valueOf(ticket.getId()),
                        ticket.getMerchantName(),
                        ticket.getPurchaseDate().toLocalDate().toString(),
                        ticket.getTotalAmount(),
                        ticket.getCategory()
                ))
                .toList();
    }

    @Transactional
    public WarrantyResponse setWarranty(User user, UUID ticketId, WarrantyRequest request) {
        Ticket ticket = findSavedTicket(user, ticketId);

        LocalDate endDate = computeEndDate(ticket, request);

        if (ChronoUnit.DAYS.between(endDate, LocalDate.now()) > KEEP_EXPIRED_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette garantie est terminée depuis plus d'un an");
        }

        ticket.setWarrantyDate(endDate);

        return toResponse(ticketRepository.save(ticket), LocalDate.now());
    }

    @Transactional
    public void clearWarranty(User user, UUID ticketId) {
        Ticket ticket = findSavedTicket(user, ticketId);

        ticket.setWarrantyDate(null);

        ticketRepository.save(ticket);
    }

    private Ticket findSavedTicket(User user, UUID ticketId) {
        if (ticketId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le ticket est obligatoire");
        }

        Ticket ticket = ticketRepository
                .findByIdAndUser(ticketId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket introuvable"));

        if (ticket.getScanStatus() != ScanStatus.SAVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce ticket n'est pas encore enregistré");
        }

        return ticket;
    }

    private LocalDate computeEndDate(Ticket ticket, WarrantyRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La durée de garantie est obligatoire");
        }

        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            try {
                return LocalDate.parse(request.getEndDate().trim());
            } catch (DateTimeParseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date invalide, format attendu : yyyy-MM-dd");
            }
        }

        Integer months = request.getMonths();

        if (months == null || months < 1 || months > MAX_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La durée de garantie doit être comprise entre 1 et 120 mois");
        }

        if (ticket.getPurchaseDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce ticket n'a pas de date d'achat");
        }

        return ticket.getPurchaseDate().toLocalDate().plusMonths(months);
    }

    private WarrantyResponse toResponse(Ticket ticket, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, ticket.getWarrantyDate());

        String status;

        if (daysLeft < 0) {
            status = "expired";
        } else if (daysLeft <= EXPIRING_DAYS) {
            status = "expiring";
        } else {
            status = "active";
        }

        String note = ticket.getNote() == null ? null : ticket.getNote().trim();

        if (note != null && note.isEmpty()) {
            note = null;
        }

        return new WarrantyResponse(
                String.valueOf(ticket.getId()),
                ticket.getMerchantName(),
                note,
                ticket.getPurchaseDate() == null ? null : ticket.getPurchaseDate().toLocalDate().toString(),
                ticket.getWarrantyDate().toString(),
                daysLeft,
                status
        );
    }
}
