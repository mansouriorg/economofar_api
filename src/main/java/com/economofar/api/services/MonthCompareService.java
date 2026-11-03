package com.economofar.api.services;

import com.economofar.api.dtos.MonthCompareResponse;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the saved tickets of a month and of the month before it and asks MonthCompareCalculator
 * to compare them. A receipt dated tomorrow (a phone that is ahead of the server) counts as today.
 */
@Service
@RequiredArgsConstructor
public class MonthCompareService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public MonthCompareResponse compare(User user, String month) {
        return compare(user, month, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public MonthCompareResponse compare(User user, String month, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        YearMonth requested = parseMonth(month);
        YearMonth current = MonthCompareCalculator.clampMonth(requested, today);
        LocalDate first = current.minusMonths(1).atDay(1);

        // A receipt dated tomorrow counts as today, even when tomorrow is the 1st of the next month
        LocalDate last = current.equals(YearMonth.from(today)) ? today.plusDays(1) : current.atEndOfMonth();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                first.atStartOfDay(),
                last.atTime(LocalTime.MAX)
        );

        List<MonthCompareCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            LocalDate date = ticket.getPurchaseDate().toLocalDate();

            if (date.isAfter(today) && !date.isAfter(today.plusDays(1))) {
                date = today;
            }

            entries.add(new MonthCompareCalculator.Entry(ticket.getCategory(), ticket.getTotalAmount(), date));
        }

        return MonthCompareCalculator.analyse(entries, today, current);
    }

    /** Null for a missing or blank month, a 400 for anything that is not exactly yyyy-MM. */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }

        String text = month.strip();

        if (!text.matches("\\d{4}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must look like 2026-10");
        }

        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must look like 2026-10");
        }
    }
}
