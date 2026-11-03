package com.economofar.api.services;

import com.economofar.api.dtos.RecurringExpenseResponse;
import com.economofar.api.dtos.SpendingForecastResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the saved tickets of the current month and of the three months before,
 * the regular expenses and the monthly budget, then asks SpendingForecaster for the estimate.
 */
@Service
@RequiredArgsConstructor
public class SpendingForecastService {

    private static final int HISTORY_MONTHS = 3;

    private final TicketRepository ticketRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final RecurringExpenseService recurringExpenseService;

    @Transactional(readOnly = true)
    public SpendingForecastResponse forecast(User user) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.withDayOfMonth(1).minusMonths(HISTORY_MONTHS);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                since.atStartOfDay(),
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

        return SpendingForecaster.forecast(spends, recurring, budget, today);
    }
}
