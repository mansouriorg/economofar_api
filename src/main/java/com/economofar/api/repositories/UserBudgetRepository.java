package com.economofar.api.repositories;

import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserBudgetRepository extends JpaRepository<UserBudget, Long> {

    /**
     * Récupère le budget mensuel configuré pour un utilisateur.
     * Utilisé dans le dashboard pour calculer le budget restant.
     */
    Optional<UserBudget> findByUser(User user);

    void deleteByUser(User user);
}