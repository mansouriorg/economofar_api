package com.economofar.api.controllers;

import com.economofar.api.dtos.ReceiptDetailResponse;
import com.economofar.api.dtos.ReceiptDetailUpdateRequest;
import com.economofar.api.dtos.ReceiptItemDetailRequest;
import com.economofar.api.dtos.ReceiptItemDetailResponse;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import com.economofar.api.services.ReceiptDetailService;
import com.economofar.api.services.ReceiptPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReceiptDetailController {

    private final ReceiptDetailService receiptDetailService;
    private final ReceiptPdfService receiptPdfService;
    private final TicketRepository ticketRepository;

    /**
     * Full receipt detail page.
     *
     * GET /api/v1/receipts/{receiptId}
     */
    @GetMapping("/{receiptId}")
    public ResponseEntity<ReceiptDetailResponse> getReceiptDetail(
            @PathVariable UUID receiptId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        ReceiptDetailResponse response = receiptDetailService.getReceiptDetail(
                receiptId,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Update full receipt detail.
     *
     * PUT /api/v1/receipts/{receiptId}
     */
    @PutMapping("/{receiptId}")
    public ResponseEntity<ReceiptDetailResponse> updateReceiptDetail(
            @PathVariable UUID receiptId,
            @RequestBody ReceiptDetailUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        ReceiptDetailResponse response = receiptDetailService.updateReceiptDetail(
                receiptId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete receipt.
     *
     * DELETE /api/v1/receipts/{receiptId}
     */
    @DeleteMapping("/{receiptId}")
    public ResponseEntity<Void> deleteReceipt(
            @PathVariable UUID receiptId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        receiptDetailService.deleteReceipt(
                receiptId,
                authenticatedUser
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Duplicate receipt.
     *
     * POST /api/v1/receipts/{receiptId}/duplicate
     */
    @PostMapping("/{receiptId}/duplicate")
    public ResponseEntity<ReceiptDetailResponse> duplicateReceipt(
            @PathVariable UUID receiptId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        ReceiptDetailResponse response = receiptDetailService.duplicateReceipt(
                receiptId,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Add item.
     *
     * POST /api/v1/receipts/{receiptId}/items
     */
    @PostMapping("/{receiptId}/items")
    public ResponseEntity<ReceiptItemDetailResponse> addItem(
            @PathVariable UUID receiptId,
            @RequestBody ReceiptItemDetailRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        ReceiptItemDetailResponse response = receiptDetailService.addItem(
                receiptId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Update item.
     *
     * PUT /api/v1/receipts/{receiptId}/items/{itemId}
     */
    @PutMapping("/{receiptId}/items/{itemId}")
    public ResponseEntity<ReceiptItemDetailResponse> updateItem(
            @PathVariable UUID receiptId,
            @PathVariable UUID itemId,
            @RequestBody ReceiptItemDetailRequest request,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        ReceiptItemDetailResponse response = receiptDetailService.updateItem(
                receiptId,
                itemId,
                request,
                authenticatedUser
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete item.
     *
     * DELETE /api/v1/receipts/{receiptId}/items/{itemId}
     */
    @DeleteMapping("/{receiptId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID receiptId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        receiptDetailService.deleteItem(
                receiptId,
                itemId,
                authenticatedUser
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Download one receipt as PDF.
     *
     * GET /api/v1/receipts/{receiptId}/pdf
     */
    @GetMapping("/{receiptId}/pdf")
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable UUID receiptId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        Ticket ticket = getTicketForUser(receiptId, authenticatedUser);

        byte[] file = receiptPdfService.buildReceiptPdf(ticket);

        return ResponseEntity.ok()
                .headers(downloadHeaders(
                        "economofar-receipt-" + receiptId + ".pdf",
                        MediaType.APPLICATION_PDF_VALUE
                ))
                .body(file);
    }

    /**
     * Export one receipt for accountant as CSV.
     *
     * GET /api/v1/receipts/{receiptId}/accountant-export/csv
     */
    @GetMapping("/{receiptId}/accountant-export/csv")
    public ResponseEntity<byte[]> exportForAccountantCsv(
            @PathVariable UUID receiptId,
            @AuthenticationPrincipal User user
    ) {
        User authenticatedUser = requireAuthenticatedUser(user);

        byte[] file = receiptDetailService.exportAccountantCsv(
                receiptId,
                authenticatedUser
        );

        return ResponseEntity.ok()
                .headers(downloadHeaders(
                        "economofar-accountant-receipt-" + receiptId + ".csv",
                        "text/csv; charset=UTF-8"
                ))
                .body(file);
    }

    private Ticket getTicketForUser(UUID receiptId, User user) {
        return ticketRepository
                .findByIdAndUser(receiptId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Receipt not found"
                ));
    }

    private HttpHeaders downloadHeaders(String filename, String contentType) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\""
        );

        headers.add(HttpHeaders.CONTENT_TYPE, contentType);

        return headers;
    }

    private User requireAuthenticatedUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        return user;
    }
}