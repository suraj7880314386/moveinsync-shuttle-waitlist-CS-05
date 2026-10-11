package com.moveinsync.shuttle.repository;

import com.moveinsync.shuttle.domain.CostCentreQuota;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCentreQuotaRepository extends JpaRepository<CostCentreQuota, Long> {
    List<CostCentreQuota> findByTripId(Long tripId);
    Optional<CostCentreQuota> findByTripIdAndCostCentre(Long tripId, String costCentre);
}
