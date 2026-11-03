package com.economofar.api.controllers;

import com.economofar.api.dtos.RecurringExpensesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/recurring
 * -> subscriptions and regular purchases found in the saved tickets.
 */
@RestController
@RequestMapping("/api/analytics/recurring")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public ResponseEntity<RecurringExpensesResponse> detect(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recurringExpenseService.detect(user));
    }
}
