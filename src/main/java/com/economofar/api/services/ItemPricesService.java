package com.economofar.api.services;

import com.economofar.api.dtos.ItemPriceDetailResponse;
import com.economofar.api.dtos.ItemPricesResponse;
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
 * Reads the articles of the saved tickets of the last months, then asks ItemPricesCalculator for the
 * list of the articles whose price is followed or for the detail of one of them. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class ItemPricesService {

    private static final int MAX_KEY_LENGTH = 255;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public ItemPricesResponse overview(User user, int months) {
        return overview(user, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ItemPricesResponse overview(User user, int months, LocalDate today) {
        requireUser(user);

        return ItemPricesCalculator.analyse(readLines(user, months, today), today, months);
    }

    @Transactional(readOnly = true)
    public ItemPriceDetailResponse detail(User user, String key, int months) {
        return detail(user, key, months, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ItemPriceDetailResponse detail(User user, String key, int months, LocalDate today) {
        requireUser(user);

        if (key == null || ItemPricesCalculator.itemKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'article est obligatoire.");
        }

        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'article est trop long.");
        }

        ItemPriceDetailResponse detail = ItemPricesCalculator.detail(readLines(user, months, today), key, today, months);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Le prix de cet article n'est pas suivi sur cette p\u00e9riode."
            );
        }

        return detail;
    }

    private List<ItemPricesCalculator.Line> readLines(User user, int months, LocalDate today) {
        LocalDate from = ItemPricesCalculator.periodStart(today, months);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                from.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<ItemPricesCalculator.Line> lines = new ArrayList<>();
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
                lines.add(new ItemPricesCalculator.Line(
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
