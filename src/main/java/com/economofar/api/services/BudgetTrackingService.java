package com.economofar.api.services;

import com.economofar.api.dtos.BudgetTrackingResponse;
import com.economofar.api.models.CategoryBudget;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.CategoryBudgetRepository;
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
 * Reads the budgets stored for the person and the saved tickets of the month, and asks
 * BudgetTrackingCalculator to follow the first against the second. Nothing is changed: the budgets
 * are edited on the budgets screen, through CategoryBudgetService.
 */
@Service
@RequiredArgsConstructor
public class BudgetTrackingService {

    private final TicketRepository ticketRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;

    @Transactional(readOnly = true)
    public BudgetTrackingResponse tracking(User user, String month) {
        return tracking(user, month, LocalDate.now());
    }

    /** Same as above for a given day, so that the days around the end of a month can be tested. */
    public BudgetTrackingResponse tracking(User user, String month, LocalDate today) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        BudgetTrackingCalculator.Period period;

        try {
            period = BudgetTrackingCalculator.period(today, month);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        List<BudgetTrackingCalculator.Budget> budgets = new ArrayList<>();

        for (CategoryBudget stored : categoryBudgetRepository.findByUserOrderByCategoryAsc(user)) {
            budgets.add(new BudgetTrackingCalculator.Budget(stored.getCategory(), stored.getMonthlyAmount()));
        }

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                period.from().atStartOfDay(),
                period.to().atTime(LocalTime.MAX)
        );

        List<BudgetTrackingCalculator.Entry> entries = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            entries.add(new BudgetTrackingCalculator.Entry(
                    ticket.getId(),
                    ticket.getMerchantName(),
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        return BudgetTrackingCalculator.analyse(entries, budgets, today, month);
    }
}
