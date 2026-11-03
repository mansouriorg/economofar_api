package com.economofar.api.services;

import com.economofar.api.dtos.DailyBudgetResponse;
import com.economofar.api.dtos.RecurringExpenseResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the saved tickets of the current month, the regular expenses and the monthly budget,
 * then asks DailyBudgetCalculator how much can still be spent today.
 */
@Service
@RequiredArgsConstructor
public class DailyBudgetService {

    private final TicketRepository ticketRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final RecurringExpenseService recurringExpenseService;

    @Transactional(readOnly = true)
    public DailyBudgetResponse today(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                monthStart.atStartOfDay(),
                today.atTime(LocalTime.MAX)
        );

        List<SpendingForecaster.Spend> spends = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            spends.add(new SpendingForecaster.Spend(
                    ticket.getMerchantName(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        List<SpendingForecaster.Recurring> recurring = new ArrayList<>();

        for (RecurringExpenseResponse item : recurringExpenseService.detect(user).items()) {
            if (item == null || item.nextExpectedDate() == null) {
                continue;
            }

            try {
                recurring.add(new SpendingForecaster.Recurring(
                        item.merchantName(),
                        item.typicalAmount(),
                        LocalDate.parse(item.nextExpectedDate()),
                        item.intervalDays()
                ));
            } catch (DateTimeParseException ignored) {
                // a malformed date only means this regular expense is not counted
            }
        }

        BigDecimal budget = userBudgetRepository.findByUser(user)
                .map(found -> found.getMonthlyAmount())
                .filter(amount -> amount != null && amount > 0)
                .map(amount -> BigDecimal.valueOf(amount.doubleValue()))
                .orElse(null);

        return DailyBudgetCalculator.compute(spends, recurring, budget, today);
    }
}
