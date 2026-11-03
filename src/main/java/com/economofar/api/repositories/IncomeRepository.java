package com.economofar.api.repositories;

import com.economofar.api.models.Income;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByUserOrderByStartDateDescIdDesc(User user);

    Optional<Income> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
