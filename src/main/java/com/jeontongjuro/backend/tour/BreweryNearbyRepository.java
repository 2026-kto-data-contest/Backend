package com.jeontongjuro.backend.tour;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 근접 캐시 리포지토리. 복합 PK = {@link BreweryNearbyId}.
 */
public interface BreweryNearbyRepository extends JpaRepository<BreweryNearby, BreweryNearbyId> {

    List<BreweryNearby> findByBreweryId(String breweryId);

    boolean existsByBreweryIdAndContentId(String breweryId, String contentId);
}
