package com.economofar.api.repositories;

import com.economofar.api.models.SavingsContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsContributionRepository extends JpaRepository<SavingsContribution, Long> {
}
