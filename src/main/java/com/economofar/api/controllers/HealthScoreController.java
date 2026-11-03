package com.economofar.api.controllers;

import com.economofar.api.dtos.HealthScoreResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.HealthScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health-score
 *
 * The financial health score of the signed-in user, with the detail of each part.
 */
@RestController
@RequestMapping("/api/health-score")
@RequiredArgsConstructor
public class HealthScoreController {

    private final HealthScoreService healthScoreService;

    @GetMapping
    public ResponseEntity<HealthScoreResponse> score(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(healthScoreService.compute(user));
    }
}
