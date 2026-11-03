package com.economofar.api.services;

import com.economofar.api.models.ScanStatus;
import com.economofar.api.models.Ticket;
import com.economofar.api.repositories.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Keeps the tickets table clean:
 *
 * 1. At startup, old tickets saved before the scan-status feature existed
 *    (status UPLOADING but with a real merchant name) are marked SAVED,
 *    so they appear in the history and the dashboard.
 *
 * 2. Every hour, scans that were started but never saved
 *    (placeholder "Analyse en cours") are deleted after 24 hours,
 *    together with their uploaded image.
 */
@Service
public class TicketMaintenanceService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TicketMaintenanceService.class);

    private static final String PLACEHOLDER_MERCHANT = "Analyse en cours";

    private final TicketRepository ticketRepository;
    private final FileStorageService fileStorageService;
    private final long abandonedScanHours;

    public TicketMaintenanceService(
            TicketRepository ticketRepository,
            FileStorageService fileStorageService,
            @Value("${TICKETS_ABANDONED_SCAN_HOURS:24}") long abandonedScanHours
    ) {
        this.ticketRepository = ticketRepository;
        this.fileStorageService = fileStorageService;
        this.abandonedScanHours = Math.max(1L, abandonedScanHours);
    }

    @Override
    @Transactional
    public void run(String... args) {
        repairLegacyTickets();
        purgeAbandonedScans();
    }

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void scheduledPurge() {
        purgeAbandonedScans();
    }

    private void repairLegacyTickets() {
        List<Ticket> candidates = ticketRepository.findByScanStatus(ScanStatus.UPLOADING);
        int repaired = 0;

        for (Ticket ticket : candidates) {
            boolean placeholder = ticket.getMerchantName() == null
                    || ticket.getMerchantName().isBlank()
                    || PLACEHOLDER_MERCHANT.equals(ticket.getMerchantName());

            if (placeholder || ticket.getSavedAt() != null) {
                continue;
            }

            LocalDateTime savedAt = ticket.getPurchaseDate() != null
                    ? ticket.getPurchaseDate()
                    : LocalDateTime.now();

            ticket.setScanStatus(ScanStatus.SAVED);
            ticket.setReviewedAt(savedAt);
            ticket.setSavedAt(savedAt);
            ticket.setScanErrorMessage(null);

            ticketRepository.save(ticket);
            repaired++;
        }

        if (repaired > 0) {
            log.info("Legacy tickets marked as SAVED: {}", repaired);
        }
    }

    private void purgeAbandonedScans() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(abandonedScanHours);

        List<ScanStatus> unfinished = List.of(
                ScanStatus.UPLOADING,
                ScanStatus.ANALYZING,
                ScanStatus.OCR_COMPLETED,
                ScanStatus.NEEDS_REVIEW,
                ScanStatus.FAILED
        );

        List<Ticket> abandoned = ticketRepository.findByScanStatusInAndScanStartedAtBefore(unfinished, cutoff);
        int deleted = 0;

        for (Ticket ticket : abandoned) {
            if (!PLACEHOLDER_MERCHANT.equals(ticket.getMerchantName()) || ticket.getSavedAt() != null) {
                continue;
            }

            String imageUrl = ticket.getImageUrl();

            ticketRepository.delete(ticket);

            if (imageUrl != null && ticketRepository.countByImageUrl(imageUrl) == 0) {
                fileStorageService.deleteByUrl(imageUrl);
            }

            deleted++;
        }

        if (deleted > 0) {
            log.info("Abandoned scans deleted: {}", deleted);
        }
    }
}
