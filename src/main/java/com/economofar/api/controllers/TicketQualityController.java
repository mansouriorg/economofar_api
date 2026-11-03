package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketQualityResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.TicketQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/quality?months=12
 * -> the saved tickets of the last months (1 to 24) that have a problem: unknown shop, category
 * "Autre", possible duplicate, missing amount or date after today.
 */
@RestController
@RequestMapping("/api/analytics/quality")
@RequiredArgsConstructor
public class TicketQualityController {

    private final TicketQualityService ticketQualityService;

    @GetMapping
    public ResponseEntity<TicketQualityResponse> analyse(
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ticketQualityService.analyse(user, months));
    }
}
