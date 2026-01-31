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
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    // POST : Créer un ticket
    @PostMapping
    public ResponseEntity<Ticket> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user // ✨ MAGIE : Spring injecte l'user connecté ici
    ) {
        return ResponseEntity.ok(service.createTicket(request, user));
    }

    // GET : Voir mes tickets
    @GetMapping
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(service.getMyTickets(user));
    }
}