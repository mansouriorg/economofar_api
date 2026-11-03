package com.economofar.api.services;

import com.economofar.api.dtos.WeeklyDigestResponse;
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
 * Collects the saved tickets of the last 14 days, then asks WeeklyDigestCalculator for the summary
 * of the last 7 days compared with the 7 days before.
 */
@Service
@RequiredArgsConstructor
public class WeeklyDigestService {

    private static final int DAYS = 14;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public WeeklyDigestResponse digest(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        LocalDate today = LocalDate.now();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                today.minusDays(DAYS - 1L).atStartOfDay(),
                today.atTime(LocalTime.MAX)
        );

        List<WeeklyDigestCalculator.Purchase> purchases = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            purchases.add(new WeeklyDigestCalculator.Purchase(
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return WeeklyDigestCalculator.compute(purchases, today);
    }
}
