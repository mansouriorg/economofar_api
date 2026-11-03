package com.economofar.api.controllers;

import com.economofar.api.dtos.UnusualPurchasesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.UnusualPurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/unusual?days=30
 * -> the purchases of the last days (7 to 90) that are far above what the user usually spends.
 */
@RestController
@RequestMapping("/api/analytics/unusual")
@RequiredArgsConstructor
public class UnusualPurchaseController {

    private final UnusualPurchaseService unusualPurchaseService;

    @GetMapping
    public ResponseEntity<UnusualPurchasesResponse> analyse(
            @RequestParam(name = "days", defaultValue = "30") int days,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(unusualPurchaseService.analyse(user, days));
    }
}
