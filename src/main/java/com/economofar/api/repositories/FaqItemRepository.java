package com.economofar.api.repositories;

import com.economofar.api.models.FaqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FaqItemRepository extends JpaRepository<FaqItem, UUID> {

    List<FaqItem> findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

    List<FaqItem> findByActiveTrueAndCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtAsc(String category);
}