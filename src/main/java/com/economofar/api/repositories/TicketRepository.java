package com.economofar.api.repositories;

import com.economofar.api.models.Ticket;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Finds all tickets belonging to a specific user and sorts them 
     * by purchase date so the newest scans appear first on the dashboard.
     */
    List<Ticket> findByUserOrderByPurchaseDateDesc(User user);

    /**
     * Alternative: If you prefer to query specifically by the UUID of the user.
     * Note: Ensure the field name in Ticket.java is 'user'.
     */
    List<Ticket> findAllByUserIdOrderByPurchaseDateDesc(UUID userId);
}