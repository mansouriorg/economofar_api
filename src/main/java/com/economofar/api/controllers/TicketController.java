package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.services.OllamaService;
import com.economofar.api.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Autorise les requêtes depuis l'application mobile
public class TicketController {

    private final TicketService service;
    private final OllamaService ollamaService;

    /**
     * ÉTAPE 1 : ANALYSE (Upload)
     * Reçoit l'image, l'envoie à l'IA (Ollama), et retourne le JSON brut à l'iPhone.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadTicket(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        System.out.println("📬 [TicketController] Analyse demandée par : " + (user != null ? user.getEmail() : "INCONNU"));
        
        // Appel au service Ollama pour l'OCR et l'extraction
        String jsonResult = ollamaService.analyzeReceipt(file);
        
        return ResponseEntity.ok(jsonResult);
    }

    /**
     * ÉTAPE 2 : SAUVEGARDE (Scan)
     * Reçoit les données validées par l'utilisateur (JSON) et les enregistre en BDD.
     */
    @PostMapping("/scan")
    public ResponseEntity<?> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user
    ) {
        System.out.println("💾 [TicketController] DÉBUT DE SAUVEGARDE");

        // 1. Vérification de l'utilisateur
        if (user == null) {
            System.err.println("❌ ERREUR: Utilisateur non identifié (AuthenticationPrincipal is null)");
            return ResponseEntity.status(401).body("User not authenticated");
        }
        System.out.println("👤 Utilisateur: " + user.getEmail() + " (ID: " + user.getId() + ")");

        // 2. Logs de debug complets pour vérifier les données reçues
        System.out.println("📦 Données reçues: ");
        System.out.println("   - Enseigne: " + request.getMerchantName());
        System.out.println("   - Montant: " + request.getTotalAmount());
        System.out.println("   - Date (String): " + request.getPurchaseDate()); 

        // 3. Tentative de sauvegarde avec gestion d'erreurs explicite
        try {
            Ticket createdTicket = service.createTicket(request, user);
            System.out.println("✅ SUCCÈS: Ticket sauvegardé avec ID " + createdTicket.getId());
            return ResponseEntity.ok(createdTicket);
        } catch (Exception e) {
            System.err.println("❌ ÉCHEC SAUVEGARDE: " + e.getMessage());
            e.printStackTrace(); // Affiche la trace complète de l'erreur dans la console
            return ResponseEntity.badRequest().body("Erreur de sauvegarde: " + e.getMessage());
        }
    }

    /**
     * ÉTAPE 3 : HISTORIQUE
     * Récupère la liste des tickets de l'utilisateur connecté.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        System.out.println("📜 [TicketController] Chargement de l'historique pour : " + (user != null ? user.getEmail() : "INCONNU"));
        
        List<Ticket> history = service.getMyTickets(user);
        
        return ResponseEntity.ok(history);
    }
}