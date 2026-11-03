package com.economofar.api.repositories;

import com.economofar.api.models.SavingsGoal;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    @EntityGraph(attributePaths = "contributions")
    List<SavingsGoal> findByUserOrderByCreatedAtAsc(User user);

    @EntityGraph(attributePaths = "contributions")
    Optional<SavingsGoal> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
