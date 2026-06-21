package com.economofar.api.repositories;

import com.economofar.api.models.SupportRequest;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, UUID> {

    List<SupportRequest> findTop50ByUserOrderByCreatedAtDesc(User user);
}