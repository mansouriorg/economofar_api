package com.economofar.api.services;

import com.economofar.api.dtos.ShopDetailResponse;
import com.economofar.api.dtos.ShopsResponse;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the saved tickets of the last months, then asks ShopsCalculator for the list of the shops or for
 * the detail of one of them. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class ShopsService {

    private static final int MAX_KEY_LENGTH = 255;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public ShopsResponse overview(User user, int months) {
        return overview(user, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ShopsResponse overview(User user, int months, LocalDate today) {
        requireUser(user);

        return ShopsCalculator.overview(readRows(user, months, today), today, months);
    }

    @Transactional(readOnly = true)
    public ShopDetailResponse detail(User user, String key, int months) {
        return detail(user, key, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ShopDetailResponse detail(User user, String key, int months, LocalDate today) {
        requireUser(user);

        if (key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'enseigne est obligatoire.");
        }

        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'enseigne est trop long.");
        }

        if (ShopsCalculator.shopKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'enseigne est obligatoire.");
        }

        ShopDetailResponse detail = ShopsCalculator.detail(readRows(user, months, today), key, today, months);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cette enseigne n'a pas de ticket enregistr\u00e9 sur cette p\u00e9riode."
            );
        }

        return detail;
    }

    /**
     * The saved tickets of the history. A ticket dated tomorrow at the latest is counted as today (the phone
     * and the server can be in different time zones); the calculator ignores the later ones.
     */
    private List<ShopsCalculator.Row> readRows(User user, int months, LocalDate today) {
        LocalDate from = ShopsCalculator.historyStart(today, months);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                from.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<ShopsCalculator.Row> rows = new ArrayList<>();
        int position = 0;

        for (Ticket ticket : tickets) {
            position++;

            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            String id = ticket.getId() == null ? "position-" + position : ticket.getId().toString();

            rows.add(new ShopsCalculator.Row(
                    id,
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    date
            ));
        }

        return rows;
    }

    private static void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }
}
