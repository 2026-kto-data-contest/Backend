package com.jeontongjuro.backend.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBreweryLinkRepository extends JpaRepository<ProductBreweryLink, Long> {

    boolean existsBySourceRowRef(Integer sourceRowRef);

    List<ProductBreweryLink> findByJoinSource(JoinSource joinSource);

    /** 제품 목록 조회용 — 한 양조장의 연결 제품(링크)을 모두 읽는다(brewery_id 인덱스 사용). */
    List<ProductBreweryLink> findByBreweryId(String breweryId);

    /** 주종 MANUAL 시드 적재 시 제품 참조(source_row_ref)로 연결 확정 brewery_id를 도출한다(uq라 최대 1건). */
    Optional<ProductBreweryLink> findBySourceRowRef(Integer sourceRowRef);

    /**
     * 조회 API 배치 로딩용 — 페이지의 brewery_id 집합으로 도수 범위를 한 번에 집계해 N+1을 피한다.
     * 양조장별 alcohol_min의 최솟값·alcohol_max의 최댓값을 낸다.
     * <p>
     * ★SQL {@code MIN/MAX}는 NULL을 무시한다 — 도수 미상(null) 링크는 집계에서 자연히 빠지고, 어떤 양조장의
     * 모든 링크가 null이거나 링크가 없으면 결과 행 자체가 없다(호출자는 그 경우 min/max를 null로 응답한다).
     * 일부 링크만 null이면 나머지 non-null 값으로 정상 산출된다.
     *
     * @return {@code [breweryId(String), minAbv(BigDecimal), maxAbv(BigDecimal)]} 튜플 목록
     */
    @Query("""
            SELECT l.breweryId, MIN(l.alcoholMin), MAX(l.alcoholMax)
            FROM ProductBreweryLink l
            WHERE l.breweryId IN :breweryIds
            GROUP BY l.breweryId
            """)
    List<Object[]> aggregateAbvByBreweryIdIn(@Param("breweryIds") Collection<String> breweryIds);
}
