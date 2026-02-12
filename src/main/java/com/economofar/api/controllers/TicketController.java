package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.services.TicketService;
import com.economofar.api.services.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Nécessaire pour les appels depuis l'iPhone (CORS)
public class TicketController {

    private final TicketService service;
    private final OllamaService ollamaService;

    /**
     * ÉTAPE 1 : ANALYSE
     * Envoie l'image à Ollama et renvoie le JSON à l'iPhone pour validation.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadTicket(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        System.out.println("📬 [TicketController] Analyse demandée par : " + user.getEmail());
        
        // Appel au service Ollama pour l'OCR et l'extraction
        String jsonResult = ollamaService.analyzeReceipt(file);
        
        return ResponseEntity.ok(jsonResult);
    }

    /**
     * ÉTAPE 2 : SAUVEGARDE
     * Enregistre les données validées par l'utilisateur en base de données.
     */
    @PostMapping("/scan")
    public ResponseEntity<Ticket> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user
    ) {
        // Log de debug pour vérifier si les données arrivent bien du mobile
        System.out.println("💾 [TicketController] Tentative de sauvegarde pour : " + user.getEmail());
        System.out.println("📦 Data: " + request.getMerchantName() + " | Montant: " + request.getTotalAmount());

        Ticket createdTicket = service.createTicket(request, user);
        
        return ResponseEntity.ok(createdTicket);
    }

    /**
     * ÉTAPE 3 : HISTORIQUE
     * Renvoie tous les tickets de l'utilisateur connecté.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        System.out.println("📜 [TicketController] Chargement de l'historique pour : " + user.getEmail());
        List<Ticket> history = service.getMyTickets(user);
        return ResponseEntity.ok(history);
    }
}