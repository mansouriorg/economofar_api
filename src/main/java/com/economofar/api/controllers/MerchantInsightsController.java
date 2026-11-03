package com.economofar.api.controllers;

import com.economofar.api.dtos.MerchantDetailResponse;
import com.economofar.api.dtos.MerchantListResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.MerchantInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/merchants?months=12
 * -> the shops where the money went, the biggest first.
 *
 * GET /api/analytics/merchants/detail?name=Carrefour&months=12
 * -> everything about one shop.
 */
@RestController
@RequestMapping("/api/analytics/merchants")
@RequiredArgsConstructor
public class MerchantInsightsController {

    private final MerchantInsightsService merchantInsightsService;

    @GetMapping
    public ResponseEntity<MerchantListResponse> list(
            @RequestParam(defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(merchantInsightsService.list(user, months));
    }

    @GetMapping("/detail")
    public ResponseEntity<MerchantDetailResponse> detail(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(merchantInsightsService.detail(user, name, months));
    }
}
