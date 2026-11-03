package com.economofar.api.controllers;

import com.economofar.api.dtos.CategoryProfilesResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CategoryProfilesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/category-profiles?months=12
 * -> one profile per category of the saved tickets of the last months: spending, tickets, average
 * basket, share of the spending, main merchants and latest tickets, the category with most
 * spending first (months from 1 to 24, 12 when omitted; a number out of this range is brought back
 * into it). Nothing is changed by this call.
 */
@RestController
@RequestMapping("/api/analytics/category-profiles")
@RequiredArgsConstructor
public class CategoryProfilesController {

    private final CategoryProfilesService categoryProfilesService;

    @GetMapping
    public ResponseEntity<CategoryProfilesResponse> profiles(
            @RequestParam(name = "months", required = false) Integer months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(categoryProfilesService.profiles(user, months));
    }
}
