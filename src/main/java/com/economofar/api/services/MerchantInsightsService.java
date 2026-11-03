package com.economofar.api.services;

import com.economofar.api.dtos.MerchantDetailResponse;
import com.economofar.api.dtos.MerchantListResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the saved tickets of the last months, then asks MerchantInsightsCalculator for the
 * ranking of the shops or for the detail of one shop.
 */
@Service
@RequiredArgsConstructor
public class MerchantInsightsService {

    private static final int MAX_NAME_LENGTH = 255;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public MerchantListResponse list(User user, int requestedMonths) {
        requireUser(user);

        int months = MerchantInsightsCalculator.clampMonths(requestedMonths);
        LocalDate today = LocalDate.now();
        LocalDate from = MerchantInsightsCalculator.periodStart(today, months);

        List<Ticket> tickets = readTickets(user, from, today);

        return MerchantInsightsCalculator.list(toPurchases(tickets, today, null), today, months);
    }

    @Transactional(readOnly = true)
    public MerchantDetailResponse detail(User user, String name, int requestedMonths) {
        requireUser(user);

        if (name == null || RecurringExpenseDetector.normalize(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du commerce est obligatoire.");
        }

        if (name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du commerce est trop long.");
        }

        int months = MerchantInsightsCalculator.clampMonths(requestedMonths);
        LocalDate today = LocalDate.now();
        LocalDate since = MerchantInsightsCalculator.fetchStart(today, months);

        List<Ticket> tickets = readTickets(user, since, today);

        MerchantDetailResponse detail = MerchantInsightsCalculator.detail(
                toPurchases(tickets, today, MerchantInsightsCalculator.merchantKey(name)),
                name,
                today,
                months
        );

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun achat enregistré chez ce commerce sur cette période."
            );
        }

        return detail;
    }

    private List<Ticket> readTickets(User user, LocalDate from, LocalDate today) {
        return ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                from.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );
    }

    private static void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
    }

    private static List<MerchantInsightsCalculator.Purchase> toPurchases(
            List<Ticket> tickets,
            LocalDate today,
            String itemsOfMerchantKey
    ) {
        List<MerchantInsightsCalculator.Purchase> purchases = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            List<MerchantInsightsCalculator.Item> items = new ArrayList<>();

            if (itemsOfMerchantKey != null
                    && itemsOfMerchantKey.equals(MerchantInsightsCalculator.merchantKey(ticket.getMerchantName()))
                    && ticket.getItems() != null) {
                for (TicketItem item : ticket.getItems()) {
                    items.add(new MerchantInsightsCalculator.Item(
                            item.getName(),
                            item.getQuantity(),
                            itemTotal(item)
                    ));
                }
            }

            purchases.add(new MerchantInsightsCalculator.Purchase(
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    date,
                    items
            ));
        }

        return purchases;
    }

    private static BigDecimal itemTotal(TicketItem item) {
        if (item.getTotalPrice() != null) {
            return item.getTotalPrice();
        }

        if (item.getUnitPrice() != null) {
            int quantity = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            return item.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        }

        return null;
    }
}
