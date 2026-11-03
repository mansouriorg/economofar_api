package com.economofar.api.services;

import com.economofar.api.dtos.AnalyticsSummaryResponse;
import com.economofar.api.dtos.AnalyticsSummaryResponse.CategoryTotal;
import com.economofar.api.dtos.AnalyticsSummaryResponse.MonthlyTotal;
import com.economofar.api.dtos.AnalyticsSummaryResponse.NamedTotal;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final String UNKNOWN_CATEGORY = "Autre";
    private static final String UNKNOWN_PAYMENT = "Non précisé";
    private static final String UNKNOWN_MERCHANT = "Commerçant inconnu";

    private final TicketRepository ticketRepository;

    public AnalyticsService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Builds the analytics of the saved tickets of the last {@code months} months.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(User user, int requestedMonths) {
        int months = Math.min(Math.max(requestedMonths, 1), 24);

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);

        LocalDateTime start = firstMonth.atDay(1).atStartOfDay();
        LocalDateTime end = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                start,
                end
        );

        Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();
        Map<YearMonth, Long> monthlyCounts = new LinkedHashMap<>();

        for (YearMonth month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            monthlyTotals.put(month, BigDecimal.ZERO);
            monthlyCounts.put(month, 0L);
        }

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> merchantTotals = new LinkedHashMap<>();
        Map<String, Long> merchantCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> paymentTotals = new LinkedHashMap<>();
        Map<String, Long> paymentCounts = new LinkedHashMap<>();

        BigDecimal totalSpent = BigDecimal.ZERO;
        long ticketCount = 0;

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null) {
                continue;
            }

            BigDecimal amount = ticket.getTotalAmount() != null ? ticket.getTotalAmount() : BigDecimal.ZERO;

            LocalDateTime date = ticket.getPurchaseDate();
            YearMonth month = YearMonth.of(date.getYear(), date.getMonthValue());

            totalSpent = totalSpent.add(amount);
            ticketCount++;

            monthlyTotals.merge(month, amount, BigDecimal::add);
            monthlyCounts.merge(month, 1L, Long::sum);

            String category = label(ticket.getCategory(), UNKNOWN_CATEGORY);
            categoryTotals.merge(category, amount, BigDecimal::add);
            categoryCounts.merge(category, 1L, Long::sum);

            String merchant = label(ticket.getMerchantName(), UNKNOWN_MERCHANT);
            merchantTotals.merge(merchant, amount, BigDecimal::add);
            merchantCounts.merge(merchant, 1L, Long::sum);

            String payment = label(ticket.getPaymentMethod(), UNKNOWN_PAYMENT);
            paymentTotals.merge(payment, amount, BigDecimal::add);
            paymentCounts.merge(payment, 1L, Long::sum);
        }

        BigDecimal averageTicket = ticketCount > 0
                ? totalSpent.divide(BigDecimal.valueOf(ticketCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<CategoryTotal> categories = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            double percentage = totalSpent.signum() > 0
                    ? entry.getValue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalSpent, 1, RoundingMode.HALF_UP)
                            .doubleValue()
                    : 0.0;

            categories.add(new CategoryTotal(
                    entry.getKey(),
                    money(entry.getValue()),
                    categoryCounts.getOrDefault(entry.getKey(), 0L),
                    percentage
            ));
        }

        categories.sort(Comparator.comparing(CategoryTotal::total).reversed());

        List<MonthlyTotal> monthly = new ArrayList<>();

        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyTotals.entrySet()) {
            monthly.add(new MonthlyTotal(
                    entry.getKey().toString(),
                    money(entry.getValue()),
                    monthlyCounts.getOrDefault(entry.getKey(), 0L)
            ));
        }

        return new AnalyticsSummaryResponse(
                months,
                start.toLocalDate().toString(),
                end.toLocalDate().minusDays(1).toString(),
                money(totalSpent),
                ticketCount,
                averageTicket,
                categories,
                monthly,
                topNamed(merchantTotals, merchantCounts, 5),
                topNamed(paymentTotals, paymentCounts, 6)
        );
    }

    private List<NamedTotal> topNamed(
            Map<String, BigDecimal> totals,
            Map<String, Long> counts,
            int limit
    ) {
        List<NamedTotal> result = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            result.add(new NamedTotal(
                    entry.getKey(),
                    money(entry.getValue()),
                    counts.getOrDefault(entry.getKey(), 0L)
            ));
        }

        result.sort(Comparator.comparing(NamedTotal::total).reversed());

        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String label(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}
