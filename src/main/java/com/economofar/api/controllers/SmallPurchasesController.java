package com.economofar.api.controllers;

import com.economofar.api.dtos.SmallPurchasesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SmallPurchasesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/small-purchases?months=6&threshold=10
 * -> what the purchases of at most threshold euros came to over the last finished months
 * (months from 3 to 12, 6 when omitted; threshold from 1 to 100, 10 when omitted; a number out of
 * these ranges is brought back into them).
 */
@RestController
@RequestMapping("/api/analytics/small-purchases")
@RequiredArgsConstructor
public class SmallPurchasesController {

    private final SmallPurchasesService smallPurchasesService;

    @GetMapping
    public ResponseEntity<SmallPurchasesResponse> smallPurchases(
            @RequestParam(name = "months", required = false) Integer months,
            @RequestParam(name = "threshold", required = false) Integer threshold,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(smallPurchasesService.smallPurchases(user, months, threshold));
    }
}
