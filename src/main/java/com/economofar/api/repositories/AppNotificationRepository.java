package com.economofar.api.repositories;

import com.economofar.api.models.AppNotification;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop50ByUserOrderByCreatedAtDesc(User user);

    Optional<AppNotification> findByIdAndUser(Long id, User user);
}