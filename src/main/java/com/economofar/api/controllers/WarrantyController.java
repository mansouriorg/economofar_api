package com.economofar.api.controllers;

import com.economofar.api.dtos.WarrantyCandidateResponse;
import com.economofar.api.dtos.WarrantyRequest;
import com.economofar.api.dtos.WarrantyResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.WarrantyService;
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
import java.util.UUID;

/**
 * GET    /api/warranties                 -> receipts that have a warranty end date
 * GET    /api/warranties/candidates      -> recent saved receipts without warranty yet
 * PUT    /api/warranties/{ticketId}      -> set the warranty of a receipt (months or endDate)
 * DELETE /api/warranties/{ticketId}      -> remove the warranty of a receipt
 */
@RestController
@RequestMapping("/api/warranties")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService warrantyService;

    @GetMapping
    public ResponseEntity<List<WarrantyResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(warrantyService.list(user));
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<WarrantyCandidateResponse>> candidates(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(warrantyService.candidates(user));
    }

    @PutMapping("/{ticketId}")
    public ResponseEntity<WarrantyResponse> setWarranty(
            @AuthenticationPrincipal User user,
            @PathVariable("ticketId") UUID ticketId,
            @RequestBody WarrantyRequest request
    ) {
        return ResponseEntity.ok(warrantyService.setWarranty(user, ticketId, request));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> clearWarranty(
            @AuthenticationPrincipal User user,
            @PathVariable("ticketId") UUID ticketId
    ) {
        warrantyService.clearWarranty(user, ticketId);
        return ResponseEntity.noContent().build();
    }
}
