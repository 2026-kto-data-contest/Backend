package com.jeontongjuro.backend.brewery;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * brewery 마스터 dimension 리포지토리. PK = BRW-xxx(String).
 * <p>
 * {@link JpaSpecificationExecutor}: 리스트 조회 API의 동적 필터 조합(region·visit·keyword 등)을
 * Specification으로 얹기 위한 확장 — 후속 주종·도수 필터도 Specification 추가만으로 붙는다.
 */
public interface BreweryRepository extends JpaRepository<Brewery, String>, JpaSpecificationExecutor<Brewery> {

    @Query("SELECT b FROM Brewery b WHERE b.latitude BETWEEN :south AND :north "
            + "AND b.longitude BETWEEN :west AND :east")
    List<Brewery> findWithinBounds(@Param("south") BigDecimal south, @Param("north") BigDecimal north,
                                   @Param("west") BigDecimal west, @Param("east") BigDecimal east);

    /**
     * region 칩별 양조장 수(필터 메타데이터 조회용). 8칩 GROUP BY 1쿼리 — 칩별로 따로 세지 않는다.
     * region이 아직 파싱되지 않은 행(null)도 그룹 하나로 나올 수 있어 호출자가 null 그룹을 건너뛴다.
     *
     * @return {@code [region(String), breweryCount(Long)]} 튜플 목록
     */
    @Query("SELECT b.region, COUNT(b) FROM Brewery b GROUP BY b.region")
    List<Object[]> countGroupByRegion();
}
