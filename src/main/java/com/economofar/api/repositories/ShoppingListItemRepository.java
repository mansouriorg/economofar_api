package com.economofar.api.repositories;

import com.economofar.api.models.ShoppingListItem;
import com.economofar.api.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    List<ShoppingListItem> findByUserOrderByIdAsc(User user);

    List<ShoppingListItem> findByUserAndCheckedTrue(User user);

    Optional<ShoppingListItem> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
