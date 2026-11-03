package com.economofar.api.repositories;

import com.economofar.api.models.SpendingEvent;
import com.economofar.api.models.SpendingEventTicket;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpendingEventTicketRepository extends JpaRepository<SpendingEventTicket, Long> {

    List<SpendingEventTicket> findByUser(User user);

    List<SpendingEventTicket> findByEvent(SpendingEvent event);

    Optional<SpendingEventTicket> findByEventAndTicketId(SpendingEvent event, UUID ticketId);

    List<SpendingEventTicket> findByUserAndTicketIdIn(User user, Collection<UUID> ticketIds);

    long countByEvent(SpendingEvent event);

    void deleteByEvent(SpendingEvent event);

    void deleteByUser(User user);
}
