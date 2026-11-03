package com.economofar.api.services;

import com.economofar.api.dtos.CategoryBudgetSuggestionResponse;
import com.economofar.api.dtos.CategoryBudgetSuggestionsResponse;
import com.economofar.api.models.CategoryBudget;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.CategoryBudgetRepository;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks at what the user spent per category in the last finished months and proposes limits
 * for the categories that have none yet.
 */
@Service
@RequiredArgsConstructor
public class CategoryBudgetSuggestionService {

    private final TicketRepository ticketRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;

    @Transactional(readOnly = true)
    public CategoryBudgetSuggestionsResponse suggest(User user) {
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);

        LocalDateTime from = current.minusMonths(CategoryBudgetSuggestionCalculator.WINDOW_MONTHS)
                .atDay(1)
                .atStartOfDay();
        LocalDateTime to = current.atDay(1).atStartOfDay().minusNanos(1);

        List<Ticket> tickets = ticketRepository.findByUserAndScanStatusAndPurchaseDateBetween(
                user,
                ScanStatus.SAVED,
                from,
                to
        );

        List<CategoryBudgetSuggestionCalculator.Spend> spends = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket.getPurchaseDate() == null || ticket.getTotalAmount() == null) {
                continue;
            }

            spends.add(new CategoryBudgetSuggestionCalculator.Spend(
                    ticket.getCategory(),
                    ticket.getTotalAmount(),
                    ticket.getPurchaseDate().toLocalDate()
            ));
        }

        Set<String> existing = new HashSet<>();

        for (CategoryBudget budget : categoryBudgetRepository.findByUserOrderByCategoryAsc(user)) {
            existing.add(CategoryBudgetSuggestionCalculator.key(budget.getCategory()));
        }

        CategoryBudgetSuggestionCalculator.Result result =
                CategoryBudgetSuggestionCalculator.compute(spends, existing, today);

        List<CategoryBudgetSuggestionResponse> items = result.suggestions()
                .stream()
                .map(item -> new CategoryBudgetSuggestionResponse(
                        item.category(),
                        item.averageSpent(),
                        item.suggestedAmount(),
                        item.monthsUsed()
                ))
                .toList();

        return new CategoryBudgetSuggestionsResponse(result.basedOnMonths(), items);
    }
}
