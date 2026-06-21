package com.economofar.api.services;

import com.economofar.api.dtos.DashboardInsightsResponse;
import com.economofar.api.dtos.DashboardRecentTicketResponse;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.repositories.UserBudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DashboardInsightService {

    private final TicketRepository ticketRepository;
    private final UserBudgetRepository userBudgetRepository;

    public DashboardInsightService(
            TicketRepository ticketRepository,
            UserBudgetRepository userBudgetRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.userBudgetRepository = userBudgetRepository;
    }

    public DashboardInsightsResponse getInsights(User user) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startOfWeek = now
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();

        LocalDateTime startOfThisMonth = now
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay();

        LocalDateTime startOfNextMonth = startOfThisMonth.plusMonths(1);

        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);
        LocalDateTime endOfLastMonth = startOfThisMonth;

        List<Ticket> allTickets = ticketRepository.findByUserOrderByPurchaseDateDesc(user);

        List<Ticket> weekTickets = ticketRepository.findByUserAndPurchaseDateBetween(
                user,
                startOfWeek,
                now
        );

        List<Ticket> thisMonthTickets = ticketRepository.findByUserAndPurchaseDateBetween(
                user,
                startOfThisMonth,
                startOfNextMonth
        );

        List<Ticket> lastMonthTickets = ticketRepository.findByUserAndPurchaseDateBetween(
                user,
                startOfLastMonth,
                endOfLastMonth
        );

        List<Ticket> recentTickets = ticketRepository.findTop5ByUserOrderByPurchaseDateDesc(user);

        Ticket lastTicket = ticketRepository.findTopByUserOrderByPurchaseDateDesc(user);

        BigDecimal totalScannedAmount = calculateTotal(allTickets);
        long ticketCount = allTickets.size();

        BigDecimal averageBasket = calculateAverage(totalScannedAmount, ticketCount);

        BigDecimal totalSpentThisWeek = calculateTotal(weekTickets);
        BigDecimal totalSpentThisMonth = calculateTotal(thisMonthTickets);
        BigDecimal totalSpentLastMonth = calculateTotal(lastMonthTickets);

        Double monthlyComparisonPercentage = calculateMonthlyComparisonPercentage(
                totalSpentThisMonth,
                totalSpentLastMonth
        );

        Ticket highestTicket = findHighestTicket(allTickets);

        BigDecimal highestReceiptAmount = highestTicket != null && highestTicket.getTotalAmount() != null
                ? normalizeAmount(highestTicket.getTotalAmount())
                : zeroAmount();

        String highestReceiptMerchant = highestTicket != null
                ? safeMerchantName(highestTicket.getMerchantName())
                : null;

        String mostFrequentMerchant = findMostFrequentMerchant(allTickets);

        BudgetSmartCardResult budgetSmartCard = calculateBudgetSmartCard(
                user,
                totalSpentThisMonth
        );

        LastScanSmartCardResult lastScanSmartCard = calculateLastScanSmartCard(lastTicket);

        List<DashboardRecentTicketResponse> recentTicketResponses = recentTickets
                .stream()
                .map(DashboardRecentTicketResponse::fromTicket)
                .toList();

        return new DashboardInsightsResponse(
                normalizeAmount(totalScannedAmount),
                ticketCount,
                normalizeAmount(averageBasket),
                normalizeAmount(totalSpentThisWeek),
                normalizeAmount(totalSpentThisMonth),
                normalizeAmount(totalSpentLastMonth),
                monthlyComparisonPercentage,
                highestReceiptAmount,
                highestReceiptMerchant,
                normalizeAmount(averageBasket),
                mostFrequentMerchant,
                budgetSmartCard.monthlyBudget(),
                budgetSmartCard.budgetRemaining(),
                budgetSmartCard.budgetExceeded(),
                lastScanSmartCard.status(),
                lastScanSmartCard.merchant(),
                lastScanSmartCard.amount(),
                lastScanSmartCard.date(),
                recentTicketResponses
        );
    }

    private BudgetSmartCardResult calculateBudgetSmartCard(
            User user,
            BigDecimal totalSpentThisMonth
    ) {
        UserBudget budget = userBudgetRepository.findByUser(user).orElse(null);

        if (budget == null || budget.getMonthlyAmount() == null) {
            return new BudgetSmartCardResult(
                    zeroAmount(),
                    zeroAmount(),
                    false
            );
        }

        BigDecimal monthlyBudget = toBigDecimal(budget.getMonthlyAmount());
        BigDecimal normalizedSpent = totalSpentThisMonth != null
                ? totalSpentThisMonth
                : BigDecimal.ZERO;

        BigDecimal budgetRemaining = monthlyBudget.subtract(normalizedSpent);
        boolean budgetExceeded = budgetRemaining.compareTo(BigDecimal.ZERO) < 0;

        return new BudgetSmartCardResult(
                normalizeAmount(monthlyBudget),
                normalizeAmount(budgetRemaining),
                budgetExceeded
        );
    }

    private LastScanSmartCardResult calculateLastScanSmartCard(Ticket lastTicket) {
        if (lastTicket == null) {
            return new LastScanSmartCardResult(
                    "READY_TO_SCAN",
                    null,
                    zeroAmount(),
                    null
            );
        }

        return new LastScanSmartCardResult(
                "COMPLETED",
                safeMerchantName(lastTicket.getMerchantName()),
                normalizeAmount(lastTicket.getTotalAmount()),
                lastTicket.getPurchaseDate()
        );
    }

    private BigDecimal calculateTotal(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return tickets.stream()
                .map(Ticket::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAverage(BigDecimal total, long count) {
        if (total == null || count <= 0) {
            return BigDecimal.ZERO;
        }

        return total.divide(
                BigDecimal.valueOf(count),
                2,
                RoundingMode.HALF_UP
        );
    }

    private Double calculateMonthlyComparisonPercentage(
            BigDecimal thisMonthTotal,
            BigDecimal lastMonthTotal
    ) {
        if (thisMonthTotal == null || lastMonthTotal == null) {
            return null;
        }

        if (lastMonthTotal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal difference = thisMonthTotal.subtract(lastMonthTotal);

        BigDecimal percentage = difference
                .divide(lastMonthTotal, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return percentage.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Ticket findHighestTicket(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return null;
        }

        return tickets.stream()
                .filter(ticket -> ticket.getTotalAmount() != null)
                .max(Comparator.comparing(Ticket::getTotalAmount))
                .orElse(null);
    }

    private String findMostFrequentMerchant(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return null;
        }

        Map<String, Long> merchantCounts = new LinkedHashMap<>();

        for (Ticket ticket : tickets) {
            String merchantName = safeMerchantName(ticket.getMerchantName());

            if (merchantName == null) {
                continue;
            }

            merchantCounts.put(
                    merchantName,
                    merchantCounts.getOrDefault(merchantName, 0L) + 1
            );
        }

        return merchantCounts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String safeMerchantName(String merchantName) {
        if (merchantName == null) {
            return null;
        }

        String cleaned = merchantName.trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        return cleaned;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return zeroAmount();
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal bigDecimalValue) {
            return bigDecimalValue;
        }

        if (value instanceof Number numberValue) {
            return BigDecimal.valueOf(numberValue.doubleValue());
        }

        if (value instanceof String stringValue) {
            try {
                return new BigDecimal(stringValue.replace(",", ".").trim());
            } catch (NumberFormatException exception) {
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    private record BudgetSmartCardResult(
            BigDecimal monthlyBudget,
            BigDecimal budgetRemaining,
            boolean budgetExceeded
    ) {
    }

    private record LastScanSmartCardResult(
            String status,
            String merchant,
            BigDecimal amount,
            LocalDateTime date
    ) {
    }
}