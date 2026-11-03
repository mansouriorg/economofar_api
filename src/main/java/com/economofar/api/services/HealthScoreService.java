package com.economofar.api.services;

import com.economofar.api.dtos.CashFlowResponse;
import com.economofar.api.dtos.CategoryBudgetResponse;
import com.economofar.api.dtos.HealthScoreResponse;
import com.economofar.api.dtos.RecurringExpensesResponse;
import com.economofar.api.dtos.SavingsGoalResponse;
import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gathers what the other services already know about the user and turns it into one score.
 */
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private static final int MONTHS = 6;

    private final CashFlowService cashFlowService;
    private final BudgetService budgetService;
    private final CategoryBudgetService categoryBudgetService;
    private final SavingsGoalService savingsGoalService;
    private final RecurringExpenseService recurringExpenseService;

    @Transactional(readOnly = true)
    public HealthScoreResponse compute(User user) {
        CashFlowResponse flow = cashFlowService.report(user, MONTHS);

        UserBudget budget = budgetService.getBudget(user);
        Double monthlyBudget = budget == null ? null : budget.getMonthlyAmount();

        List<CategoryBudgetResponse> categoryBudgets = categoryBudgetService.list(user);
        List<SavingsGoalResponse> goals = savingsGoalService.list(user);
        RecurringExpensesResponse recurring = recurringExpenseService.detect(user);

        return HealthScoreCalculator.compute(
                HealthScoreCalculator.inputFrom(flow, monthlyBudget, categoryBudgets, goals, recurring)
        );
    }
}
