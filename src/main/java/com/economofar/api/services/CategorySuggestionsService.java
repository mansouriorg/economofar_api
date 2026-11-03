package com.economofar.api.services;

import com.economofar.api.dtos.CategorySuggestionsResponse;
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
 * Reads the saved tickets of the last 24 months and asks CategorySuggestionsCalculator which of
 * the ones without category can be given the category the same merchant already has. Nothing is
 * changed: a category is only changed when the user applies a suggestion.
 */
@Service
@RequiredArgsConstructor
public class CategorySuggestionsService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public CategorySuggestionsResponse suggestions(User user, Integer months) {
        return suggestions(user, months, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public CategorySuggestionsResponse suggestions(User user, Integer months, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                CategorySuggestionsCalculator.learningStart(today).atStartOfDay(),
                today.atTime(LocalTime.MAX)
        );

        List<CategorySuggestionsCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            entries.add(new CategorySuggestionsCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return CategorySuggestionsCalculator.analyse(entries, today, months);
    }
}
