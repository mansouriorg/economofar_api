package com.economofar.api.controllers;

import com.economofar.api.dtos.CashFlowResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CashFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/cashflow?months=6
 *
 * Income against spending for the last N months (1 to 12, default 6).
 */
@RestController
@RequestMapping("/api/cashflow")
@RequiredArgsConstructor
public class CashFlowController {

    private final CashFlowService cashFlowService;

    @GetMapping
    public ResponseEntity<CashFlowResponse> report(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "months", defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(cashFlowService.report(user, months));
    }
}
