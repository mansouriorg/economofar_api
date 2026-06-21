package com.economofar.api.repositories;

import com.economofar.api.models.TicketItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketItemRepository extends JpaRepository<TicketItem, UUID> {
}