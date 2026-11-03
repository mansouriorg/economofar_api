package com.economofar.api.services;

import com.economofar.api.dtos.UnusualPurchasesResponse;
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
 * Reads the saved tickets of the last days and of the six months before, then asks
 * UnusualPurchaseCalculator which purchases are far above the usual. A receipt dated tomorrow (a
 * phone that is ahead of the server) counts as today.
 */
@Service
@RequiredArgsConstructor
public class UnusualPurchaseService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public UnusualPurchasesResponse analyse(User user, int requestedDays) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int days = UnusualPurchaseCalculator.clampDays(requestedDays);
        LocalDate today = LocalDate.now();
        LocalDate since = UnusualPurchaseCalculator.fetchStart(today, days);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                since.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<UnusualPurchaseCalculator.Purchase> purchases = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            purchases.add(new UnusualPurchaseCalculator.Purchase(
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    date
            ));
        }

        return UnusualPurchaseCalculator.analyse(purchases, today, days);
    }
}
