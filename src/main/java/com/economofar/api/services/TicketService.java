package com.economofar.api.services;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // ✅ Critical Import

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;

    /**
     * Sauvegarder un ticket pour un utilisateur spécifique.
     * Mappe le DTO vers l'entité et gère la conversion de date.
     */
    @Transactional // ✅ Ensures the database transaction is committed
    public Ticket createTicket(TicketRequest request, User user) {
        
        LocalDateTime parsedDate;
        try {
            // ✅ Robust Date Parsing
            // Swift sends dates like "2023-10-27T10:00:00Z"
            if (request.getPurchaseDate() != null && !request.getPurchaseDate().isEmpty()) {
                parsedDate = LocalDateTime.parse(request.getPurchaseDate(), DateTimeFormatter.ISO_DATE_TIME);
            } else {
                parsedDate = LocalDateTime.now();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Date parsing failed for input: '" + request.getPurchaseDate() + "'. Using NOW().");
            parsedDate = LocalDateTime.now();
        }

        // ✅ Default image URL to prevent database constraint errors
        String validImageUrl = (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) 
                ? request.getImageUrl() 
                : "placeholder.jpg";

        var ticket = Ticket.builder()
                .merchantName(request.getMerchantName())
                .totalAmount(request.getTotalAmount())
                .purchaseDate(parsedDate)
                .category(request.getCategory())
                .imageUrl(validImageUrl) // ✅ Prevents "Column 'image_url' cannot be null"
                .rawOcrText(request.getRawOcrText())
                .user(user)
                .build();
        
        return repository.save(ticket);
    }

    /**
     * Récupérer les tickets de l'utilisateur connecté.
     */
    public List<Ticket> getMyTickets(User user) {
        return repository.findByUserOrderByPurchaseDateDesc(user);
    }
}