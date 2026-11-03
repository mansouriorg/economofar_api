package com.economofar.api.services;

import com.economofar.api.dtos.CategoryProfilesResponse;
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
 * Reads the saved tickets of the period and asks CategoryProfilesCalculator for one profile per
 * category. Nothing is changed.
 */
@Service
@RequiredArgsConstructor
public class CategoryProfilesService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public CategoryProfilesResponse profiles(User user, Integer months) {
        return profiles(user, months, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public CategoryProfilesResponse profiles(User user, Integer months, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                CategoryProfilesCalculator.windowStart(today, months).atStartOfDay(),
                today.atTime(LocalTime.MAX)
        );

        List<CategoryProfilesCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            entries.add(new CategoryProfilesCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return CategoryProfilesCalculator.analyse(entries, today, months);
    }
}
