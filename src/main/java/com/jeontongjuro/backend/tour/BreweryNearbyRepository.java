package com.jeontongjuro.backend.tour;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 근접 캐시 리포지토리. 복합 PK = {@link BreweryNearbyId}.
 */
public interface BreweryNearbyRepository extends JpaRepository<BreweryNearby, BreweryNearbyId> {

    List<BreweryNearby> findByBreweryId(String breweryId);

    /** 추천 코스 후보를 거리 오름차순으로 한 번에 조회한다. 거리 결측은 맨 뒤로 보낸다. */
    @Query("""
            SELECT n FROM BreweryNearby n
            WHERE n.breweryId = :breweryId
            ORDER BY CASE WHEN n.distanceM IS NULL THEN 1 ELSE 0 END,
                     n.distanceM ASC, n.contentId ASC
            """)
    List<BreweryNearby> findCourseCandidates(@Param("breweryId") String breweryId);

    boolean existsByBreweryIdAndContentId(String breweryId, String contentId);
}
