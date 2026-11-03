package com.economofar.api.services;

import com.economofar.api.dtos.YearReviewResponse;
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
 * Reads the saved tickets of a year and of the year before it and asks YearReviewCalculator for
 * the review. A receipt dated tomorrow (a phone that is ahead of the server) counts as today.
 */
@Service
@RequiredArgsConstructor
public class YearReviewService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public YearReviewResponse review(User user, Integer year) {
        return review(user, year, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a year can be tested. */
    public YearReviewResponse review(User user, Integer year, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int chosen = YearReviewCalculator.clampYear(year, today);
        LocalDate first = LocalDate.of(chosen - 1, 1, 1);

        // A receipt dated tomorrow counts as today, even when tomorrow is the 1st of the next year
        LocalDate last = chosen == today.getYear() ? today.plusDays(1) : LocalDate.of(chosen, 12, 31);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                first.atStartOfDay(),
                last.atTime(LocalTime.MAX)
        );

        List<YearReviewCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            entries.add(new YearReviewCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    date
            ));
        }

        return YearReviewCalculator.analyse(entries, today, chosen);
    }
}
