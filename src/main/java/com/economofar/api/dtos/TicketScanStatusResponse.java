package com.economofar.api.dtos;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;

import java.time.LocalDateTime;
import java.util.UUID;

public class TicketScanStatusResponse {

    private UUID ticketId;
    private ScanStatus scanStatus;
    private String scanErrorMessage;
    private LocalDateTime scanStartedAt;
    private LocalDateTime ocrCompletedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime savedAt;

    public TicketScanStatusResponse() {
    }

    public TicketScanStatusResponse(
            UUID ticketId,
            ScanStatus scanStatus,
            String scanErrorMessage,
            LocalDateTime scanStartedAt,
            LocalDateTime ocrCompletedAt,
            LocalDateTime reviewedAt,
            LocalDateTime savedAt
    ) {
        this.ticketId = ticketId;
        this.scanStatus = scanStatus;
        this.scanErrorMessage = scanErrorMessage;
        this.scanStartedAt = scanStartedAt;
        this.ocrCompletedAt = ocrCompletedAt;
        this.reviewedAt = reviewedAt;
        this.savedAt = savedAt;
    }

    public static TicketScanStatusResponse fromTicket(Ticket ticket) {
        return new TicketScanStatusResponse(
                ticket.getId(),
                ticket.getScanStatus(),
                ticket.getScanErrorMessage(),
                ticket.getScanStartedAt(),
                ticket.getOcrCompletedAt(),
                ticket.getReviewedAt(),
                ticket.getSavedAt()
        );
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }

    public String getScanErrorMessage() {
        return scanErrorMessage;
    }

    public LocalDateTime getScanStartedAt() {
        return scanStartedAt;
    }

    public LocalDateTime getOcrCompletedAt() {
        return ocrCompletedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}