package com.economofar.api.controllers;

import com.economofar.api.dtos.CategoryTrendsResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CategoryTrendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/analytics/category-trends?months=6
 * -> how the spending of each category moved over the last finished months (3 to 12, 6 when
 * omitted; a number out of this range is brought back into it).
 */
@RestController
@RequestMapping("/api/analytics/category-trends")
@RequiredArgsConstructor
public class CategoryTrendsController {

    private final CategoryTrendsService categoryTrendsService;

    @GetMapping
    public ResponseEntity<CategoryTrendsResponse> trends(
            @RequestParam(name = "months", required = false) Integer months,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(categoryTrendsService.trends(user, months));
    }
}
