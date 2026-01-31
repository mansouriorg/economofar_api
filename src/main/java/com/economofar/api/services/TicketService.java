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

    // Sauvegarder un ticket pour un utilisateur spécifique
    public Ticket createTicket(TicketRequest request, User user) {
        var ticket = Ticket.builder()
                .merchantName(request.getMerchantName())
                .totalAmount(request.getTotalAmount())
                .purchaseDate(request.getPurchaseDate())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .user(user) // ✅ Association importante !
                .build();
        
        return repository.save(ticket);
    }

    // Récupérer les tickets de l'utilisateur connecté
    public List<Ticket> getMyTickets(User user) {
        return repository.findAllByUserId(user.getId());
    }
}