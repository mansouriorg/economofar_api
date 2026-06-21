package com.economofar.api.services;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.repositories.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TicketScanStatusService {

    private final TicketRepository ticketRepository;

    public TicketScanStatusService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket markUploading(Ticket ticket) {
        ticket.setScanStatus(ScanStatus.UPLOADING);
        ticket.setScanStartedAt(LocalDateTime.now());
        ticket.setScanErrorMessage(null);
        return ticketRepository.save(ticket);
    }

    public Ticket markAnalyzing(Ticket ticket) {
        ticket.setScanStatus(ScanStatus.ANALYZING);
        ticket.setScanErrorMessage(null);
        return ticketRepository.save(ticket);
    }

    public Ticket markOcrCompleted(Ticket ticket) {
        ticket.setScanStatus(ScanStatus.OCR_COMPLETED);
        ticket.setOcrCompletedAt(LocalDateTime.now());
        ticket.setScanErrorMessage(null);
        return ticketRepository.save(ticket);
    }

    public Ticket markNeedsReview(Ticket ticket) {
        ticket.setScanStatus(ScanStatus.NEEDS_REVIEW);
        ticket.setReviewedAt(null);
        ticket.setScanErrorMessage(null);
        return ticketRepository.save(ticket);
    }

    public Ticket markSaved(Ticket ticket) {
        ticket.setScanStatus(ScanStatus.SAVED);
        ticket.setReviewedAt(LocalDateTime.now());
        ticket.setSavedAt(LocalDateTime.now());
        ticket.setScanErrorMessage(null);
        return ticketRepository.save(ticket);
    }

    public Ticket markFailed(Ticket ticket, Exception exception) {
        ticket.setScanStatus(ScanStatus.FAILED);
        ticket.setScanErrorMessage(
                exception != null && exception.getMessage() != null
                        ? exception.getMessage()
                        : "Unknown scan error"
        );
        return ticketRepository.save(ticket);
    }

    public Ticket markFailed(Ticket ticket, String errorMessage) {
        ticket.setScanStatus(ScanStatus.FAILED);
        ticket.setScanErrorMessage(
                errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "Unknown scan error"
        );
        return ticketRepository.save(ticket);
    }
}