package com.economofar.api.controllers;

import com.economofar.api.dtos.CategoryBudgetRequest;
import com.economofar.api.dtos.CategoryBudgetResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.CategoryBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/budget/categories        -> limits + spent this month
 * PUT    /api/budget/categories        -> create or update the limit of a category
 * DELETE /api/budget/categories/{id}   -> remove a limit
 */
@RestController
@RequestMapping("/api/budget/categories")
@RequiredArgsConstructor
public class CategoryBudgetController {

    private final CategoryBudgetService categoryBudgetService;

    @GetMapping
    public ResponseEntity<List<CategoryBudgetResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(categoryBudgetService.list(user));
    }

    @PutMapping
    public ResponseEntity<CategoryBudgetResponse> save(
            @AuthenticationPrincipal User user,
            @RequestBody CategoryBudgetRequest request
    ) {
        return ResponseEntity.ok(categoryBudgetService.save(user, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        categoryBudgetService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
