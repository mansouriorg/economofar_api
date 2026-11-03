package com.economofar.api.controllers;

import com.economofar.api.dtos.WeeklyDigestResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.WeeklyDigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/weekly-digest
 * -> the last 7 days compared with the 7 days before.
 */
@RestController
@RequestMapping("/api/analytics/weekly-digest")
@RequiredArgsConstructor
public class WeeklyDigestController {

    private final WeeklyDigestService weeklyDigestService;

    @GetMapping
    public ResponseEntity<WeeklyDigestResponse> digest(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(weeklyDigestService.digest(user));
    }
}
