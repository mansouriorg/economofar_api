package com.economofar.api.controllers;

import com.economofar.api.dtos.DashboardInsightsResponse;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.DashboardInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardInsightService dashboardInsightService;
    private final UserRepository userRepository;

    public DashboardController(
            DashboardInsightService dashboardInsightService,
            UserRepository userRepository
    ) {
        this.dashboardInsightService = dashboardInsightService;
        this.userRepository = userRepository;
    }

    @GetMapping("/insights")
    public ResponseEntity<DashboardInsightsResponse> getDashboardInsights(
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        DashboardInsightsResponse response = dashboardInsightService.getInsights(user);

        return ResponseEntity.ok(response);
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }

        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}