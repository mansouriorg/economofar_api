package com.economofar.api.controllers;

import com.economofar.api.dtos.ManualTicketRequest;
import com.economofar.api.dtos.ManualTicketResponse;
import com.economofar.api.models.User;
import com.economofar.api.services.ManualTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/tickets/manual
 *
 * Saves a ticket typed by hand: merchant, amount, day, category, payment method and a note.
 */
@RestController
@RequestMapping("/api/v1/tickets/manual")
@RequiredArgsConstructor
public class ManualTicketController {

    private final ManualTicketService manualTicketService;

    @PostMapping
    public ResponseEntity<ManualTicketResponse> create(
            @RequestBody ManualTicketRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manualTicketService.create(request, user));
    }
}
