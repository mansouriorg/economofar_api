package com.economofar.api.services;

import com.economofar.api.dtos.RepurchaseDetailResponse;
import com.economofar.api.dtos.RepurchaseResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.TicketItem;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the articles of the saved tickets of the last months, then asks RepurchaseCalculator for the list
 * of the articles bought at a regular rhythm or for the detail of one of them. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class RepurchaseService {

    private static final int MAX_KEY_LENGTH = 255;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public RepurchaseResponse overview(User user, int months) {
        return overview(user, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public RepurchaseResponse overview(User user, int months, LocalDate today) {
        requireUser(user);

        return RepurchaseCalculator.overview(readLines(user, months, today), today, months);
    }

    @Transactional(readOnly = true)
    public RepurchaseDetailResponse detail(User user, String key, int months) {
        return detail(user, key, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public RepurchaseDetailResponse detail(User user, String key, int months, LocalDate today) {
        requireUser(user);

        if (key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'article est obligatoire.");
        }

        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'article est trop long.");
        }

        if (RepurchaseCalculator.articleKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'article est obligatoire.");
        }

        RepurchaseDetailResponse detail = RepurchaseCalculator.detail(readLines(user, months, today), key, today, months);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cet article n'a pas de rythme d'achat r\u00e9gulier sur cette p\u00e9riode."
            );
        }

        return detail;
    }

    /**
     * The lines of the saved tickets of the period. A ticket dated tomorrow at the latest is counted as
     * today (the phone and the server can be in different time zones); the calculator ignores the later
     * ones.
     */
    private List<RepurchaseCalculator.Line> readLines(User user, int months, LocalDate today) {
        LocalDate from = RepurchaseCalculator.periodStart(today, months);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                from.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<RepurchaseCalculator.Line> lines = new ArrayList<>();
        int position = 0;

        for (Ticket ticket : tickets) {
            position++;

            if (ticket.getPurchaseDate() == null || ticket.getItems() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            String id = ticket.getId() == null ? "position-" + position : ticket.getId().toString();

            for (TicketItem item : ticket.getItems()) {
                lines.add(new RepurchaseCalculator.Line(
                        id,
                        item.getName(),
                        ticket.getMerchantName(),
                        date,
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice()
                ));
            }
        }

        return lines;
    }

    private static void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }
}
