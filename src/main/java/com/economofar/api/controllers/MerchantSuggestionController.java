package com.economofar.api.controllers;

import com.economofar.api.dtos.MerchantSuggestionResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.MerchantSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/merchants/suggestion?name=Carrefour
 * -> category and payment method the user usually picks for this shop.
 */
@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantSuggestionController {

    private final MerchantSuggestionService merchantSuggestionService;

    @GetMapping("/suggestion")
    public ResponseEntity<MerchantSuggestionResponse> suggest(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "name", required = false) String name
    ) {
        return ResponseEntity.ok(merchantSuggestionService.suggest(user, name));
    }
}
