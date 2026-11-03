package com.economofar.api.services;

import com.economofar.api.dtos.TicketQualityResponse;
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
 * Reads the saved tickets of the last months, and the ones dated up to a year ahead (a typo in
 * the year), then asks TicketQualityCalculator which ones have a problem. A receipt dated
 * tomorrow (a phone that is ahead of the server) counts as today.
 */
@Service
@RequiredArgsConstructor
public class TicketQualityService {

    private static final int FUTURE_DAYS = 366;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public TicketQualityResponse analyse(User user, int requestedMonths) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int months = TicketQualityCalculator.clampMonths(requestedMonths);
        LocalDate today = LocalDate.now();
        LocalDate since = TicketQualityCalculator.windowStart(today, months);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                since.atStartOfDay(),
                today.plusDays(FUTURE_DAYS).atTime(LocalTime.MAX)
        );

        List<TicketQualityCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            entries.add(new TicketQualityCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    date
            ));
        }

        return TicketQualityCalculator.analyse(entries, today, months);
    }
}
