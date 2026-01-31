package com.economofar.api.repositories;

import com.economofar.api.models.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    // Trouve tous les tickets liés à un ID utilisateur spécifique
    List<Ticket> findAllByUserId(UUID userId);
}