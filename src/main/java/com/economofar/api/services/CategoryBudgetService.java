package com.economofar.api.services;

import com.economofar.api.dtos.CategoryBudgetRequest;
import com.economofar.api.dtos.CategoryBudgetResponse;
import com.economofar.api.models.CategoryBudget;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.CategoryBudgetRepository;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Budgets per category.
 *
 * The amount spent is the sum of the SAVED tickets of the current month
 * whose category matches (case-insensitive).
 */
@Service
@RequiredArgsConstructor
public class CategoryBudgetService {

    private static final Logger log = LoggerFactory.getLogger(CategoryBudgetService.class);

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    private final CategoryBudgetRepository categoryBudgetRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<CategoryBudgetResponse> list(User user) {
        Map<String, BigDecimal> spentByCategory = spentThisMonth(user);

        return categoryBudgetRepository
                .findByUserOrderByCategoryAsc(user)
                .stream()
                .map(budget -> toResponse(
                        budget,
                        spentByCategory.getOrDefault(key(budget.getCategory()), BigDecimal.ZERO)
                ))
                .toList();
    }

    @Transactional
    public CategoryBudgetResponse save(User user, CategoryBudgetRequest request) {
        if (request == null || request.getCategory() == null || request.getCategory().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La catégorie est obligatoire");
        }

        String category = request.getCategory().trim();

        if (category.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de la catégorie est trop long");
        }

        BigDecimal rawAmount = request.getMonthlyAmount();

        if (rawAmount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant doit être supérieur à 0");
        }

        if (rawAmount.scale() > 100 || rawAmount.scale() < -100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant est invalide");
        }

        if (rawAmount.compareTo(MAX_AMOUNT) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant est trop élevé");
        }

        BigDecimal roundedAmount = rawAmount.setScale(2, RoundingMode.HALF_UP);

        if (roundedAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant doit être supérieur à 0");
        }

        CategoryBudget budget = categoryBudgetRepository
                .findFirstByUserAndCategoryIgnoreCaseOrderByIdAsc(user, category)
                .orElseGet(() -> new CategoryBudget(user, category, roundedAmount));

        budget.setMonthlyAmount(roundedAmount);

        CategoryBudget saved = categoryBudgetRepository.save(budget);

        BigDecimal spent = spentThisMonth(user).getOrDefault(key(saved.getCategory()), BigDecimal.ZERO);

        return toResponse(saved, spent);
    }

    @Transactional
    public void delete(User user, Long id) {
        CategoryBudget budget = categoryBudgetRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget introuvable"));

        categoryBudgetRepository.delete(budget);
    }

    /**
     * Called after a ticket is saved. Sends one alert per category and per month
     * when the limit of that category is exceeded. Never breaks the saving of the ticket.
     */
    public void checkCategoryBudget(User user, String category) {
        if (user == null || category == null || category.isBlank()) {
            return;
        }

        try {
            CategoryBudget budget = categoryBudgetRepository
                    .findFirstByUserAndCategoryIgnoreCaseOrderByIdAsc(user, category.trim())
                    .orElse(null);

            if (budget == null) {
                return;
            }

            BigDecimal spent = spentThisMonth(user).getOrDefault(key(budget.getCategory()), BigDecimal.ZERO);

            if (spent.compareTo(budget.getMonthlyAmount()) > 0) {
                notificationService.notifyCategoryBudgetExceeded(
                        user,
                        budget.getCategory(),
                        spent.doubleValue(),
                        budget.getMonthlyAmount().doubleValue()
                );
            }
        } catch (RuntimeException exception) {
            log.warn("Category budget check failed: {}", exception.getMessage());
        }
    }

    private Map<String, BigDecimal> spentThisMonth(User user) {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1).minusNanos(1);

        Map<String, BigDecimal> totals = new HashMap<>();

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                start,
                end
        );

        for (Ticket ticket : tickets) {
            if (ticket.getCategory() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            totals.merge(key(ticket.getCategory()), ticket.getTotalAmount(), BigDecimal::add);
        }

        return totals;
    }

    private CategoryBudgetResponse toResponse(CategoryBudget budget, BigDecimal spent) {
        BigDecimal amount = budget.getMonthlyAmount();
        BigDecimal remaining = amount.subtract(spent);

        double percentUsed = amount.signum() > 0
                ? spent.multiply(BigDecimal.valueOf(100)).divide(amount, 1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new CategoryBudgetResponse(
                budget.getId(),
                budget.getCategory(),
                amount,
                spent,
                remaining,
                percentUsed,
                spent.compareTo(amount) > 0
        );
    }

    private String key(String category) {
        return category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    }
}
