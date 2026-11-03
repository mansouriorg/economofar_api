package com.economofar.api.controllers;

import com.economofar.api.dtos.AnalyticsSummaryResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics/summary?months=6
     *
     * Category breakdown, monthly trend, top merchants and payment methods
     * for the last N months (1 to 24, default 6).
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @RequestParam(defaultValue = "6") int months,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        return ResponseEntity.ok(analyticsService.getSummary(user, months));
    }
}
