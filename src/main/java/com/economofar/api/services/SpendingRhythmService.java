package com.economofar.api.services;

import com.economofar.api.dtos.SpendingRhythmResponse;
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
 * Reads the saved tickets of the last weeks and asks SpendingRhythmCalculator when the user
 * spends. A receipt dated tomorrow (a phone that is ahead of the server) counts as today.
 */
@Service
@RequiredArgsConstructor
public class SpendingRhythmService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public SpendingRhythmResponse analyse(User user, int requestedWeeks) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int weeks = SpendingRhythmCalculator.clampWeeks(requestedWeeks);
        LocalDate today = LocalDate.now();
        LocalDate since = SpendingRhythmCalculator.windowStart(today, weeks);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                since.atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<SpendingRhythmCalculator.Purchase> purchases = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            purchases.add(new SpendingRhythmCalculator.Purchase(ticket.getTotalAmount(), date));
        }

        return SpendingRhythmCalculator.analyse(purchases, today, weeks);
    }
}
