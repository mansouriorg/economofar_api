package com.economofar.api.controllers;

import com.economofar.api.dtos.TicketEditableRequest;
import com.economofar.api.dtos.TicketEditableResponse;
import com.economofar.api.dtos.TicketItemRequest;
import com.economofar.api.dtos.TicketItemResponse;
import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.dtos.TicketScanStatusResponse;
import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.services.OllamaService;
import com.economofar.api.services.TicketScanStatusService;
import com.economofar.api.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketController {

    private final TicketService service;
    private final OllamaService ollamaService;
    private final TicketRepository ticketRepository;
    private final TicketScanStatusService ticketScanStatusService;

    /**
     * ÉTAPE 1 : UPLOAD + OCR ANALYSIS
     *
     * Flow:
     * UPLOADING -> ANALYZING -> OCR_COMPLETED -> NEEDS_REVIEW
     *
     * This endpoint returns:
     * - ticketId
     * - scanStatus
     * - rawOcrText
     * - message
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadTicket(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        System.out.println("📬 [TicketController] Upload demandé par : " + authenticatedUser.getEmail());

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File is required"
            );
        }

        Ticket ticket = null;

        try {
            ticket = Ticket.builder()
                    .user(authenticatedUser)
                    .merchantName("Analyse en cours")
                    .purchaseDate(LocalDateTime.now())
                    .scanStatus(ScanStatus.UPLOADING)
                    .scanStartedAt(LocalDateTime.now())
                    .build();

            ticket = ticketScanStatusService.markUploading(ticket);

            System.out.println("⬆️ [TicketController] Ticket upload started. ID: " + ticket.getId());

            ticket = ticketScanStatusService.markAnalyzing(ticket);

            System.out.println("🔍 [TicketController] OCR analysis started. Ticket ID: " + ticket.getId());

            String jsonResult = ollamaService.analyzeReceipt(file);

            ticket.setRawOcrText(jsonResult);

            ticket = ticketScanStatusService.markOcrCompleted(ticket);

            System.out.println("✅ [TicketController] OCR completed. Ticket ID: " + ticket.getId());

            ticket = ticketScanStatusService.markNeedsReview(ticket);

            System.out.println("📝 [TicketController] Ticket needs user review. Ticket ID: " + ticket.getId());

            return ResponseEntity.ok(
                    buildUploadResponse(
                            ticket,
                            jsonResult,
                            "OCR completed. User review required."
                    )
            );

        } catch (Exception exception) {
            System.err.println("❌ [TicketController] OCR/upload failed: " + exception.getMessage());
            exception.printStackTrace();

            if (ticket != null) {
                ticket = ticketScanStatusService.markFailed(ticket, exception);
            }

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            buildUploadResponse(
                                    ticket,
                                    null,
                                    "OCR/upload failed: " + exception.getMessage()
                            )
                    );
        }
    }

    /**
     * ÉTAPE 2 : SAVE VALIDATED TICKET
     *
     * Receives validated data from the iPhone and saves:
     * - merchant
     * - date
     * - total
     * - category
     * - raw OCR text
     * - editable extracted items
     */
    @PostMapping("/scan")
    public ResponseEntity<?> createTicket(
            @RequestBody TicketRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        System.out.println("💾 [TicketController] Début sauvegarde ticket");
        System.out.println("👤 Utilisateur: " + authenticatedUser.getEmail() + " | ID: " + authenticatedUser.getId());

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticket request body is required"
            );
        }

        System.out.println("📦 Données reçues:");
        System.out.println("   - Enseigne: " + request.getMerchantName());
        System.out.println("   - Montant: " + request.getTotalAmount());
        System.out.println("   - Date: " + request.getPurchaseDate());
        System.out.println("   - Catégorie: " + request.getCategory());
        System.out.println("   - Items: " + (request.getItems() != null ? request.getItems().size() : 0));

        try {
            Ticket createdTicket = service.createTicket(request, authenticatedUser);

            System.out.println("✅ [TicketController] Ticket sauvegardé avec ID: " + createdTicket.getId());
            System.out.println("✅ [TicketController] Scan status: " + createdTicket.getScanStatus());

            return ResponseEntity.ok(createdTicket);

        } catch (Exception exception) {
            System.err.println("❌ [TicketController] Échec sauvegarde: " + exception.getMessage());
            exception.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Erreur de sauvegarde: " + exception.getMessage());
        }
    }

    /**
     * ÉTAPE 3 : HISTORY
     *
     * Récupère la liste des tickets de l'utilisateur connecté.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        System.out.println("📜 [TicketController] Chargement historique pour : " + authenticatedUser.getEmail());

        List<Ticket> history = service.getMyTickets(authenticatedUser);

        return ResponseEntity.ok(history);
    }

    /**
     * ÉTAPE 4 : SCAN STATUS
     *
     * Allows frontend to poll status:
     *
     * GET /api/v1/tickets/{ticketId}/scan-status
     */
    @GetMapping("/{ticketId}/scan-status")
    public ResponseEntity<TicketScanStatusResponse> getTicketScanStatus(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        Ticket ticket = ticketRepository
                .findByIdAndUser(ticketId, authenticatedUser)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found"
                ));

        return ResponseEntity.ok(TicketScanStatusResponse.fromTicket(ticket));
    }

    /**
     * ÉTAPE 5 : ACTIVE SCAN
     *
     * Useful if the app closes during OCR/review.
     * The frontend can restore the last active scan.
     *
     * GET /api/v1/tickets/active-scan
     */
    @GetMapping("/active-scan")
    public ResponseEntity<TicketScanStatusResponse> getActiveScan(
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        List<ScanStatus> activeStatuses = List.of(
                ScanStatus.UPLOADING,
                ScanStatus.ANALYZING,
                ScanStatus.OCR_COMPLETED,
                ScanStatus.NEEDS_REVIEW,
                ScanStatus.FAILED
        );

        return ticketRepository
                .findTopByUserAndScanStatusInOrderByScanStartedAtDesc(
                        authenticatedUser,
                        activeStatuses
                )
                .map(ticket -> ResponseEntity.ok(TicketScanStatusResponse.fromTicket(ticket)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * ÉTAPE 6 : GET EDITABLE TICKET
     *
     * Returns one ticket with editable extracted data:
     * - merchant
     * - date
     * - total
     * - category
     * - items
     *
     * GET /api/v1/tickets/{ticketId}/editable
     */
    @GetMapping("/{ticketId}/editable")
    public ResponseEntity<TicketEditableResponse> getEditableTicket(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        TicketEditableResponse response = service.getEditableTicket(
                ticketId,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * ÉTAPE 7 : UPDATE FULL EDITABLE TICKET
     *
     * Updates:
     * - merchant
     * - date
     * - total
     * - category
     * - raw OCR text
     * - full item list
     *
     * PUT /api/v1/tickets/{ticketId}/editable
     */
    @PutMapping("/{ticketId}/editable")
    public ResponseEntity<TicketEditableResponse> updateEditableTicket(
            @PathVariable UUID ticketId,
            @RequestBody TicketEditableRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        TicketEditableResponse response = service.updateEditableTicket(
                ticketId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * ÉTAPE 8 : ADD MISSING ITEM
     *
     * POST /api/v1/tickets/{ticketId}/items
     */
    @PostMapping("/{ticketId}/items")
    public ResponseEntity<TicketItemResponse> addItem(
            @PathVariable UUID ticketId,
            @RequestBody TicketItemRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        TicketItemResponse response = service.addItem(
                ticketId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * ÉTAPE 9 : UPDATE ONE ITEM
     *
     * Updates:
     * - item name
     * - quantity
     * - unit price
     * - total price
     * - category
     *
     * PUT /api/v1/tickets/{ticketId}/items/{itemId}
     */
    @PutMapping("/{ticketId}/items/{itemId}")
    public ResponseEntity<TicketItemResponse> updateItem(
            @PathVariable UUID ticketId,
            @PathVariable UUID itemId,
            @RequestBody TicketItemRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        TicketItemResponse response = service.updateItem(
                ticketId,
                itemId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * ÉTAPE 10 : DELETE WRONG ITEM
     *
     * DELETE /api/v1/tickets/{ticketId}/items/{itemId}
     */
    @DeleteMapping("/{ticketId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID ticketId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        service.deleteItem(
                ticketId,
                itemId,
                authenticatedUser
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * ÉTAPE 11 : CHANGE TICKET CATEGORY
     *
     * Expected body:
     * {
     *   "category": "Alimentation"
     * }
     *
     * PATCH /api/v1/tickets/{ticketId}/category
     */
    @PatchMapping("/{ticketId}/category")
    public ResponseEntity<TicketEditableResponse> changeCategory(
            @PathVariable UUID ticketId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        String category = body != null ? body.get("category") : null;

        TicketEditableResponse response = service.changeTicketCategory(
                ticketId,
                category,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    private User requireAuthenticatedUser(User user) {
        if (user == null) {
            System.err.println("❌ [TicketController] User not authenticated");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        return user;
    }

    private Map<String, Object> buildUploadResponse(
            Ticket ticket,
            String rawOcrText,
            String message
    ) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("ticketId", ticket != null ? ticket.getId() : null);
        response.put("scanStatus", ticket != null ? ticket.getScanStatus() : ScanStatus.FAILED);
        response.put("scanErrorMessage", ticket != null ? ticket.getScanErrorMessage() : message);
        response.put("scanStartedAt", ticket != null ? ticket.getScanStartedAt() : null);
        response.put("ocrCompletedAt", ticket != null ? ticket.getOcrCompletedAt() : null);
        response.put("reviewedAt", ticket != null ? ticket.getReviewedAt() : null);
        response.put("savedAt", ticket != null ? ticket.getSavedAt() : null);
        response.put("rawOcrText", rawOcrText);
        response.put("message", message);

        return response;
    }
}