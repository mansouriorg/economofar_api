package com.economofar.api.controllers;

import com.economofar.api.dtos.UpdateBudgetRequest;
import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserRepository userRepository;

    public BudgetController(
            BudgetService budgetService,
            UserRepository userRepository
    ) {
        this.budgetService = budgetService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<UserBudget> getBudget(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(budgetService.getBudget(user));
    }

    @PutMapping
    public ResponseEntity<UserBudget> updateBudget(
            Authentication authentication,
            @RequestBody UpdateBudgetRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(budgetService.updateBudget(user, request));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}