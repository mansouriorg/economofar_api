package com.economofar.api.controllers;

import com.economofar.api.dtos.BusinessExpensesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.BusinessExpensesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/business-expenses?period=year
 * -> the business tickets of the period added up: total and share of the spending, VAT, deductible
 * and reimbursable amounts, then the totals per expense report, per project and per client, with
 * the tickets that have no expense report, project or client (period: month, quarter, year or
 * previous-year; the year when omitted or unknown). Nothing is changed by this call.
 */
@RestController
@RequestMapping("/api/analytics/business-expenses")
@RequiredArgsConstructor
public class BusinessExpensesController {

    private final BusinessExpensesService businessExpensesService;

    @GetMapping
    public ResponseEntity<BusinessExpensesResponse> business(
            @RequestParam(name = "period", required = false) String period,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(businessExpensesService.business(user, period));
    }
}
