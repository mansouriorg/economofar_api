package com.economofar.api.controllers;

import com.economofar.api.dtos.CategorySuggestionsResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CategorySuggestionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/category-suggestions?months=12
 * -> the saved tickets of the last months that have no category, and the category that the same
 * merchant already has for most of its tickets (months from 1 to 24, 12 when omitted; a number out
 * of this range is brought back into it). Nothing is changed by this call.
 */
@RestController
@RequestMapping("/api/analytics/category-suggestions")
@RequiredArgsConstructor
public class CategorySuggestionsController {

    private final CategorySuggestionsService categorySuggestionsService;

    @GetMapping
    public ResponseEntity<CategorySuggestionsResponse> suggestions(
            @RequestParam(name = "months", required = false) Integer months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(categorySuggestionsService.suggestions(user, months));
    }
}
