package com.economofar.api.controllers;

import com.economofar.api.dtos.SpendingSplitResponse;
import com.economofar.api.dtos.SpendingSplitRuleRequest;
import com.economofar.api.dtos.SpendingSplitRuleResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SpendingSplitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/spending-split?month=2026-10
 * -> the spending of the month split into needs, wants and savings (the 50/30/20 rule) against the
 * income declared for the month, with the categories that make each part. The month is "yyyy-MM" and it
 * is the current one when omitted; a month that can not be read gives a 400. Nothing is changed.
 *
 * POST /api/analytics/spending-split/rules   { "category": "restaurant", "group": "WANT" }
 * -> puts a category in a group: "NEED", "WANT" or "EXCLUDED" (left out of the calculation), or
 * "DEFAULT" to go back to the proposed group. The answer is the rule as it is stored.
 */
@RestController
@RequestMapping("/api/analytics/spending-split")
@RequiredArgsConstructor
public class SpendingSplitController {

    private final SpendingSplitService spendingSplitService;

    @GetMapping
    public ResponseEntity<SpendingSplitResponse> split(
            @RequestParam(name = "month", required = false) String month,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingSplitService.split(user, month));
    }

    @PostMapping("/rules")
    public ResponseEntity<SpendingSplitRuleResponse> rule(
            @RequestBody SpendingSplitRuleRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(spendingSplitService.setRule(user, request));
    }
}
