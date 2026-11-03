package com.economofar.api.repositories;

import com.economofar.api.models.SpendingEvent;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpendingEventRepository extends JpaRepository<SpendingEvent, Long> {

    List<SpendingEvent> findByUser(User user);

    Optional<SpendingEvent> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
