package com.economofar.api.repositories;

import com.economofar.api.models.CategoryBudget;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, Long> {

    List<CategoryBudget> findByUserOrderByCategoryAsc(User user);

    Optional<CategoryBudget> findFirstByUserAndCategoryIgnoreCaseOrderByIdAsc(User user, String category);

    Optional<CategoryBudget> findByIdAndUser(Long id, User user);

    void deleteByUser(User user);
}
