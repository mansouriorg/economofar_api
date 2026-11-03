package com.economofar.api.controllers;

import com.economofar.api.dtos.YearReviewResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.YearReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/year?year=2026
 * -> the year (the year in progress when omitted) month by month, with the categories, the shops
 * where the most was spent, the biggest ticket and the comparison with the previous year.
 */
@RestController
@RequestMapping("/api/analytics/year")
@RequiredArgsConstructor
public class YearReviewController {

    private final YearReviewService yearReviewService;

    @GetMapping
    public ResponseEntity<YearReviewResponse> review(
            @RequestParam(name = "year", required = false) Integer year,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(yearReviewService.review(user, year));
    }
}
