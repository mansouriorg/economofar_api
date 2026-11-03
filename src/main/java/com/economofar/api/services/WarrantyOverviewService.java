package com.economofar.api.services;

import com.economofar.api.dtos.WarrantyOverviewResponse;
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
 * Reads the saved tickets of the last 10 years and asks WarrantyOverviewCalculator which of the ones that
 * have a warranty date are still covered. Nothing is changed. The tickets are read up to the end of
 * tomorrow, so that a ticket dated a few hours ahead by another time zone is not lost.
 */
@Service
@RequiredArgsConstructor
public class WarrantyOverviewService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public WarrantyOverviewResponse warranties(User user, Integer soonDays) {
        return warranties(user, soonDays, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a warranty can be tested. */
    public WarrantyOverviewResponse warranties(User user, Integer soonDays, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                WarrantyOverviewCalculator.purchaseStart(today).atStartOfDay(),
                today.plusDays(1).atTime(LocalTime.MAX)
        );

        List<WarrantyOverviewCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getWarrantyDate() == null) {
                continue;
            }

            entries.add(new WarrantyOverviewCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getNote(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate() == null ? null : ticket.getPurchaseDate().toLocalDate(),
                    ticket.getWarrantyDate()
            ));
        }

        return WarrantyOverviewCalculator.analyse(entries, today, soonDays);
    }
}
