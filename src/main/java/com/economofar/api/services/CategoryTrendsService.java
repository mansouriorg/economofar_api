package com.economofar.api.services;

import com.economofar.api.dtos.CategoryTrendsResponse;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the saved tickets of the last finished months and asks CategoryTrendsCalculator how the
 * spending of each category moved. The month in progress is not read.
 */
@Service
@RequiredArgsConstructor
public class CategoryTrendsService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public CategoryTrendsResponse trends(User user, Integer months) {
        return trends(user, months, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public CategoryTrendsResponse trends(User user, Integer months, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int chosen = CategoryTrendsCalculator.clampMonths(months);
        YearMonth last = YearMonth.from(today).minusMonths(1);
        YearMonth first = last.minusMonths(chosen - 1);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                first.atDay(1).atStartOfDay(),
                last.atEndOfMonth().atTime(LocalTime.MAX)
        );

        List<CategoryTrendsCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            entries.add(new CategoryTrendsCalculator.Entry(
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return CategoryTrendsCalculator.analyse(entries, today, chosen);
    }
}
