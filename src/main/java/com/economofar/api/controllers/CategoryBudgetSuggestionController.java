package com.economofar.api.controllers;

import com.economofar.api.dtos.CategoryBudgetSuggestionsResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CategoryBudgetSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/budget/categories/suggestions
 *
 * Limits that could be set for the categories that have none yet, from the last finished months.
 */
@RestController
@RequestMapping("/api/budget/categories/suggestions")
@RequiredArgsConstructor
public class CategoryBudgetSuggestionController {

    private final CategoryBudgetSuggestionService categoryBudgetSuggestionService;

    @GetMapping
    public ResponseEntity<CategoryBudgetSuggestionsResponse> suggestions(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(categoryBudgetSuggestionService.suggest(user));
    }
}
