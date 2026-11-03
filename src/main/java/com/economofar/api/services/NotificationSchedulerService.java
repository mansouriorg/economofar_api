package com.economofar.api.services;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationSchedulerService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public NotificationSchedulerService(
            UserRepository userRepository,
            TicketRepository ticketRepository,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    /**
     * Every Monday at 09:00.
     */
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklyExpenseReports() {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(7);

        List<User> users = userRepository.findAll();

        for (User user : users) {
            List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                    user,
                    ScanStatus.SAVED,
                    startDate,
                    endDate
            );

            if (tickets.isEmpty()) {
                continue;
            }

            BigDecimal totalAmount = tickets.stream()
                    .map(Ticket::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            notificationService.notifyWeeklyExpenseReport(
                    user,
                    totalAmount.doubleValue(),
                    tickets.size()
            );
        }
    }

    /**
     * First day of each month at 09:00.
     * Sends the summary for the previous month.
     */
    @Scheduled(cron = "0 0 9 1 * *")
    public void sendMonthlySpendingSummaries() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startDate = now
                .minusMonths(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        LocalDateTime endDate = startDate.plusMonths(1);

        List<User> users = userRepository.findAll();

        for (User user : users) {
            List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                    user,
                    ScanStatus.SAVED,
                    startDate,
                    endDate
            );

            if (tickets.isEmpty()) {
                continue;
            }

            BigDecimal totalAmount = tickets.stream()
                    .map(Ticket::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            notificationService.notifyMonthlySpendingSummary(
                    user,
                    totalAmount.doubleValue(),
                    tickets.size()
            );
        }
    }
}