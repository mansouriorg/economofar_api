package com.economofar.api.repositories;

import com.economofar.api.models.User;
import com.economofar.api.models.UserBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBudgetRepository extends JpaRepository<UserBudget, Long> {
    Optional<UserBudget> findByUser(User user);
}