package com.economofar.api.repositories;

import com.economofar.api.models.SpendingSplitRule;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpendingSplitRuleRepository extends JpaRepository<SpendingSplitRule, Long> {

    List<SpendingSplitRule> findByUser(User user);

    Optional<SpendingSplitRule> findByUserAndCategoryKey(User user, String categoryKey);

    long countByUser(User user);

    void deleteByUser(User user);
}
