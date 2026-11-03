package com.economofar.api.controllers;

import com.economofar.api.dtos.SpendingForecastResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SpendingForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/forecast
 * -> estimate of the total spending at the end of the current month.
 */
@RestController
@RequestMapping("/api/analytics/forecast")
@RequiredArgsConstructor
public class SpendingForecastController {

    private final SpendingForecastService spendingForecastService;

    @GetMapping
    public ResponseEntity<SpendingForecastResponse> forecast(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(spendingForecastService.forecast(user));
    }
}
