package com.economofar.api.controllers;

import com.economofar.api.dtos.SavingsContributionRequest;
import com.economofar.api.dtos.SavingsGoalRequest;
import com.economofar.api.dtos.SavingsGoalResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.SavingsGoalService;
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
 * GET    /api/savings-goals                                   -> goals with their progress
 * POST   /api/savings-goals                                   -> create a goal
 * PUT    /api/savings-goals/{id}                              -> change a goal
 * DELETE /api/savings-goals/{id}                              -> remove a goal and its contributions
 * POST   /api/savings-goals/{id}/contributions                -> put money in (or take some out)
 * DELETE /api/savings-goals/{id}/contributions/{contributionId} -> cancel a contribution
 */
@RestController
@RequestMapping("/api/savings-goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public ResponseEntity<List<SavingsGoalResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(savingsGoalService.list(user));
    }

    @PostMapping
    public ResponseEntity<SavingsGoalResponse> create(
            @AuthenticationPrincipal User user,
            @RequestBody SavingsGoalRequest request
    ) {
        return ResponseEntity.ok(savingsGoalService.create(user, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoalResponse> update(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @RequestBody SavingsGoalRequest request
    ) {
        return ResponseEntity.ok(savingsGoalService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        savingsGoalService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<SavingsGoalResponse> addContribution(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @RequestBody SavingsContributionRequest request
    ) {
        return ResponseEntity.ok(savingsGoalService.addContribution(user, id, request));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<SavingsGoalResponse> deleteContribution(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @PathVariable("contributionId") Long contributionId
    ) {
        return ResponseEntity.ok(savingsGoalService.deleteContribution(user, id, contributionId));
    }
}
