package com.economofar.api.controllers;

import com.economofar.api.dtos.PriceTrackerResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.PriceTrackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/prices
 * -> price evolution of the items found on the saved tickets.
 */
@RestController
@RequestMapping("/api/analytics/prices")
@RequiredArgsConstructor
public class PriceTrackerController {

    private final PriceTrackerService priceTrackerService;

    @GetMapping
    public ResponseEntity<PriceTrackerResponse> track(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(priceTrackerService.track(user));
    }
}
