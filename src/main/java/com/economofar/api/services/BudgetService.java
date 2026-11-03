package com.economofar.api.services;

import com.economofar.api.dtos.UpdateBudgetRequest;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BudgetService {

    private final UserBudgetRepository budgetRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public BudgetService(
            UserBudgetRepository budgetRepository,
            TicketRepository ticketRepository,
            NotificationService notificationService
    ) {
        this.budgetRepository = budgetRepository;
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    public UserBudget getBudget(User user) {
        return budgetRepository.findByUser(user).orElse(null);
    }

    public UserBudget updateBudget(User user, UpdateBudgetRequest request) {
        UserBudget budget = budgetRepository
                .findByUser(user)
                .orElseGet(() -> new UserBudget(
                        user,
                        request.getMonthlyAmount(),
                        request.getCurrency() != null ? request.getCurrency() : user.getPreferredCurrency()
                ));

        budget.setMonthlyAmount(request.getMonthlyAmount());

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            budget.setCurrency(request.getCurrency());
        }

        return budgetRepository.save(budget);
    }

    public void checkBudgetExceeded(User user) {
        UserBudget budget = budgetRepository.findByUser(user).orElse(null);

        if (budget == null || budget.getMonthlyAmount() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startDate = now
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        LocalDateTime endDate = startDate.plusMonths(1);

        List<Ticket> monthlyTickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                startDate,
                endDate
        );

        BigDecimal currentTotal = monthlyTickets.stream()
                .map(Ticket::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyBudget = BigDecimal.valueOf(budget.getMonthlyAmount());

        if (currentTotal.compareTo(monthlyBudget) > 0) {
            notificationService.notifyBudgetExceeded(
                    user,
                    currentTotal.doubleValue(),
                    monthlyBudget.doubleValue()
            );
        }
    }
}