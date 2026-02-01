package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets") // ✅ Best Practice: Versioning your API
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    /**
     * POST /api/v1/tickets/scan
     * Used to upload the data extracted from the receipt.
     */
    @PostMapping("/scan")
    public ResponseEntity<Ticket> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user // ✨ Spring Security injects the connected user
    ) {
        return ResponseEntity.ok(service.createTicket(request, user));
    }

    /**
     * GET /api/v1/tickets/history
     * Used by the Dashboard to retrieve the user's previous scans.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(service.getMyTickets(user));
    }
}