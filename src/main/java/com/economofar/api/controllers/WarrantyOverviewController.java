package com.economofar.api.controllers;

import com.economofar.api.dtos.WarrantyOverviewResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.WarrantyOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/warranties?soonDays=30
 * -> the saved tickets that have a warranty date, sorted by how much warranty is left: the ones
 * ending within soonDays days, the ones ending later, and the ones that ended in the last 12 months
 * (soonDays from 7 to 90, 30 when omitted; a number out of this range is brought back into it).
 * Nothing is changed by this call.
 */
@RestController
@RequestMapping("/api/analytics/warranties")
@RequiredArgsConstructor
public class WarrantyOverviewController {

    private final WarrantyOverviewService warrantyOverviewService;

    @GetMapping
    public ResponseEntity<WarrantyOverviewResponse> warranties(
            @RequestParam(name = "soonDays", required = false) Integer soonDays,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(warrantyOverviewService.warranties(user, soonDays));
    }
}
