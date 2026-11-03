package com.economofar.api.repositories;

import com.economofar.api.models.NotificationPreference;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUser(User user);

    void deleteByUser(User user);
}