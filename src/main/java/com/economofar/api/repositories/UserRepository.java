package com.economofar.api.repositories;

import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // Spring comprend automatiquement cette méthode et crée le SQL pour toi !
    Optional<User> findByEmail(String email);
}