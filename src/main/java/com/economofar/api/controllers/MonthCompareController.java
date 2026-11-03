package com.economofar.api.controllers;

import com.economofar.api.dtos.MonthCompareResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.MonthCompareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/compare?month=2026-10
 * -> the month (the month in progress when omitted) compared with the month before it: total,
 * tickets, change per category and the two cumulative curves. 400 when month is not yyyy-MM.
 */
@RestController
@RequestMapping("/api/analytics/compare")
@RequiredArgsConstructor
public class MonthCompareController {

    private final MonthCompareService monthCompareService;

    @GetMapping
    public ResponseEntity<MonthCompareResponse> compare(
            @RequestParam(name = "month", required = false) String month,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(monthCompareService.compare(user, month));
    }
}
