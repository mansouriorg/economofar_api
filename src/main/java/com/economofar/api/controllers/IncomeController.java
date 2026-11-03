package com.economofar.api.controllers;

import com.economofar.api.dtos.IncomeRequest;
import com.economofar.api.dtos.IncomeResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.IncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/incomes        -> the declared incomes
 * POST   /api/incomes        -> declare an income
 * PUT    /api/incomes/{id}   -> change an income
 * DELETE /api/incomes/{id}   -> remove an income
 */
@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @GetMapping
    public ResponseEntity<List<IncomeResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(incomeService.list(user));
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> create(
            @AuthenticationPrincipal User user,
            @RequestBody IncomeRequest request
    ) {
        return ResponseEntity.ok(incomeService.create(user, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncomeResponse> update(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @RequestBody IncomeRequest request
    ) {
        return ResponseEntity.ok(incomeService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        incomeService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
