package com.economofar.api.controllers;

import com.economofar.api.dtos.RepurchaseDetailResponse;
import com.economofar.api.dtos.RepurchaseResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.RepurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/repurchase?months=12
 * -> the articles of the saved tickets that are bought at a regular rhythm, with the cycle, the next
 * expected purchase and a status (due, soon, ok, lapsed), the ones to buy first at the top. months goes
 * from 3 to 24, 12 when omitted.
 *
 * GET /api/analytics/repurchase/detail?key=lait&months=12
 * -> one article: its figures, the gaps between its purchases, its latest purchases and its usual shops.
 * key is the key given by the list. A missing key gives a 400, an article without a regular rhythm a 404.
 */
@RestController
@RequestMapping("/api/analytics/repurchase")
@RequiredArgsConstructor
public class RepurchaseController {

    private final RepurchaseService repurchaseService;

    @GetMapping
    public ResponseEntity<RepurchaseResponse> overview(
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(repurchaseService.overview(user, months));
    }

    @GetMapping("/detail")
    public ResponseEntity<RepurchaseDetailResponse> detail(
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "months", defaultValue = "12") int months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(repurchaseService.detail(user, key, months));
    }
}
