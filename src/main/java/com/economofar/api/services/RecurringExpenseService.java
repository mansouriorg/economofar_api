package com.economofar.api.services;

import com.economofar.api.dtos.RecurringExpenseResponse;
import com.economofar.api.dtos.RecurringExpensesResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks at the saved tickets of the last 12 months and returns the regular expenses.
 */
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private static final int LOOKBACK_MONTHS = 12;

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public RecurringExpensesResponse detect(User user) {
        LocalDate today = LocalDate.now();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                today.minusMonths(LOOKBACK_MONTHS).atStartOfDay(),
                today.atTime(LocalTime.MAX)
        );

        List<RecurringExpenseDetector.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            entries.add(new RecurringExpenseDetector.Entry(
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        List<RecurringExpenseResponse> items = RecurringExpenseDetector.detect(entries, today);

        BigDecimal monthlyTotal = items.stream()
                .map(RecurringExpenseResponse::monthlyEstimate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RecurringExpensesResponse(items, monthlyTotal);
    }
}
