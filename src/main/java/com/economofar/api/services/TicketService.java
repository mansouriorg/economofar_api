package com.economofar.api.services;

import com.economofar.api.dtos.TicketRequest;
import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import com.economofar.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository repository;

    /**
     * Sauvegarder un ticket pour un utilisateur spécifique.
     * Utilise le Builder de Lombok pour mapper le DTO vers l'entité.
     */
    public Ticket createTicket(TicketRequest request, User user) {
        var ticket = Ticket.builder()
                .merchantName(request.getMerchantName())
                .totalAmount(request.getTotalAmount())
                .purchaseDate(request.getPurchaseDate())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .rawOcrText(request.getRawOcrText()) // ✅ Inclus pour le debug/IA
                .user(user) // ✅ Association à l'utilisateur connecté
                .build();
        
        return repository.save(ticket);
    }

    /**
     * Récupérer les tickets de l'utilisateur connecté.
     * Utilise l'entité User pour une requête JPA optimisée et triée.
     */
    public List<Ticket> getMyTickets(User user) {
        // On utilise la méthode de repository qui gère le tri par date
        return repository.findByUserOrderByPurchaseDateDesc(user);
    }
}