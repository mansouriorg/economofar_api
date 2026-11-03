package com.economofar.api.services;

import com.economofar.api.dtos.SmallPurchasesResponse;
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
 * Reads the saved tickets of the last finished months and asks SmallPurchasesCalculator how much
 * the small ones came to. The month in progress is not read.
 */
@Service
@RequiredArgsConstructor
public class SmallPurchasesService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public SmallPurchasesResponse smallPurchases(User user, Integer months, Integer threshold) {
        return smallPurchases(user, months, threshold, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public SmallPurchasesResponse smallPurchases(User user, Integer months, Integer threshold, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        int chosenMonths = SmallPurchasesCalculator.clampMonths(months);
        int chosenThreshold = SmallPurchasesCalculator.clampThreshold(threshold);
        YearMonth last = YearMonth.from(today).minusMonths(1);
        YearMonth first = last.minusMonths(chosenMonths - 1);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                first.atDay(1).atStartOfDay(),
                last.atEndOfMonth().atTime(LocalTime.MAX)
        );

        List<SmallPurchasesCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            entries.add(new SmallPurchasesCalculator.Entry(
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return SmallPurchasesCalculator.analyse(entries, today, chosenMonths, chosenThreshold);
    }
}
