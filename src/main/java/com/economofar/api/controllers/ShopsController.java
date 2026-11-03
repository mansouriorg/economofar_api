package com.economofar.api.controllers;

import com.economofar.api.dtos.ShopDetailResponse;
import com.economofar.api.dtos.ShopsResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.ShopsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/shops?months=12
 * -> the shops of the saved tickets of the period, the ones where most was spent first, with their visits,
 * average ticket, rhythm and what changed lately. months goes from 3 to 24, 12 when omitted.
 *
 * GET /api/analytics/shops/detail?key=carrefour&months=12
 * -> one shop: its figures, its months, categories, days of the week and latest tickets. key is the key
 * given by the list. A missing key gives a 400, a shop without ticket in the period a 404.
 */
@RestController
@RequestMapping("/api/analytics/shops")
@RequiredArgsConstructor
public class ShopsController {

    private final ShopsService shopsService;

    @GetMapping
    public ResponseEntity<ShopsResponse> overview(
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(shopsService.overview(user, months));
    }

    @GetMapping("/detail")
    public ResponseEntity<ShopDetailResponse> detail(
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(shopsService.detail(user, key, months));
    }
}
