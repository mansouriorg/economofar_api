package com.economofar.api.repositories;

import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Récupère un utilisateur par son email.
     * Utilisé pour l'authentification et pour récupérer l'utilisateur connecté.
     */
    Optional<User> findByEmail(String email);

    /**
     * Vérifie si un email existe déjà.
     * Utilisé lors de la modification du profil pour éviter les doublons.
     */
    boolean existsByEmail(String email);

    /**
     * Case-insensitive duplicate check used at registration.
     */
    boolean existsByEmailIgnoreCase(String email);
}