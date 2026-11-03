package com.economofar.api.controllers;

import com.economofar.api.dtos.BudgetTrackingResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.BudgetTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/budget-tracking?month=2026-10
 * -> the budgets stored for the person (the ones of GET /api/budget/categories) followed against the
 * saved tickets of the month: spent, remaining, percent used, status, projection at the end of the
 * month, what can still be spent each day, the latest tickets of each category, and the categories
 * that have spending but no budget. The month is "yyyy-MM" and it is the current one when omitted; a
 * month that can not be read gives a 400. Nothing is changed by this call.
 */
@RestController
@RequestMapping("/api/analytics/budget-tracking")
@RequiredArgsConstructor
public class BudgetTrackingController {

    private final BudgetTrackingService budgetTrackingService;

    @GetMapping
    public ResponseEntity<BudgetTrackingResponse> tracking(
            @RequestParam(name = "month", required = false) String month,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(budgetTrackingService.tracking(user, month));
    }
}
