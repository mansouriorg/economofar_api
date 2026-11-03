package com.economofar.api.controllers;

import com.economofar.api.dtos.PaymentUsageResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.PaymentUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/payment-methods?months=12
 * -> the saved tickets of the last months added up per payment method: spending, tickets, average
 * basket, share of the spending, month by month, main categories and latest tickets, the method with
 * most spending first, and the tickets that have no payment method apart (months from 1 to 24, 12
 * when omitted; a number out of this range is brought back into it). Nothing is changed by this
 * call.
 */
@RestController
@RequestMapping("/api/analytics/payment-methods")
@RequiredArgsConstructor
public class PaymentUsageController {

    private final PaymentUsageService paymentUsageService;

    @GetMapping
    public ResponseEntity<PaymentUsageResponse> usage(
            @RequestParam(name = "months", required = false) Integer months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(paymentUsageService.usage(user, months));
    }
}
