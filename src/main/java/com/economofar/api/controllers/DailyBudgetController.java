package com.economofar.api.controllers;

import com.economofar.api.dtos.DailyBudgetResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.DailyBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/daily-budget
 * -> what can still be spent today without leaving the monthly budget.
 */
@RestController
@RequestMapping("/api/analytics/daily-budget")
@RequiredArgsConstructor
public class DailyBudgetController {

    private final DailyBudgetService dailyBudgetService;

    @GetMapping
    public ResponseEntity<DailyBudgetResponse> today(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dailyBudgetService.today(user));
    }
}
