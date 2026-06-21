package com.economofar.api.controllers;

import com.economofar.api.dtos.DashboardInsightsResponse;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.DashboardInsightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * Dynamic dashboard insights endpoint.
     *
     * URL:
     * GET /api/dashboard/insights
     *
     * Requires:
     * Authorization: Bearer TOKEN
     *
     * Returns:
     * - total scanned amount
     * - number of tickets
     * - average basket
     * - weekly spending
     * - monthly spending
     * - last month comparison
     * - highest receipt
     * - most frequent merchant
     * - monthly budget
     * - budget remaining
     * - budget exceeded status
     * - last scan status
     * - recent tickets
     */
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
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Unauthenticated request"
            );
        }

        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated user not found"
                ));
    }
}