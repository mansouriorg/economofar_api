package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.services.TicketService;
import com.economofar.api.services.OllamaService; // ✅ Ajoute l'import
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // ✅ Ajoute l'import

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;
    private final OllamaService ollamaService; // ✅ Injecte ton service Ollama

    /**
     * POST /api/v1/tickets/upload
     * C'est ici que l'iPhone envoie l'image brute.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadTicket(
            @RequestParam("file") MultipartFile file, // 👈 Capture le fichier "multipart" de Swift
            @AuthenticationPrincipal User user
    ) {
        // 1. On envoie l'image à Ollama (minicpm-v) pour analyse
        String jsonResult = ollamaService.analyzeReceipt(file);
        
        // 2. On renvoie le JSON extrait par l'IA à l'iPhone
        // L'iPhone affichera ce JSON dans la ResultView pour validation
        return ResponseEntity.ok(jsonResult);
    }

    /**
     * POST /api/v1/tickets/scan
     * Appelé quand l'utilisateur valide les données sur son téléphone pour les enregistrer en BDD.
     */
    @PostMapping("/scan")
    public ResponseEntity<Ticket> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(service.createTicket(request, user));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(service.getMyTickets(user));
    }
}