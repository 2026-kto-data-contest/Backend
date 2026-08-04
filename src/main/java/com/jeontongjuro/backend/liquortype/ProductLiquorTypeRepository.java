package com.jeontongjuro.backend.liquortype;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLiquorTypeRepository extends JpaRepository<ProductLiquorType, Long> {

    /** 멱등 스킵용 — 같은 (제품, 주종) 조합이 이미 있으면 재적재하지 않는다(MANUAL·AUTO 공통). */
    boolean existsBySourceRowRefAndLiquorType(Integer sourceRowRef, LiquorType liquorType);

    /** EXCLUSION 모순 검출용 — 같은 (제품, 주종)이 MANUAL로도 있으면 authoring 모순(3d 3차, 이슈 #20). */
    boolean existsBySourceRowRefAndLiquorTypeAndSource(Integer sourceRowRef, LiquorType liquorType,
                                                        LiquorTagSource source);

    /**
     * 양조장별 주종 집계(별도 집계 테이블 없이 GROUP BY — 59행 규모라 성능 무관). 조회 API가 쓸 준비.
     * <p>
     * {@code onlyConfirmed=true}면 검수 완료분만(recheck_flag=false·suppressed_from_tab=false) 집계한다.
     * ★지금은 전 AUTO행이 recheck_flag=true라 true로 부르면 결과가 비어 있는 게 정상이다.
     *
     * @return {@code [breweryId(String), liquorType(LiquorType), productCount(Long)]} 튜플 목록
     */
    @Query("""
            SELECT t.breweryId, t.liquorType, COUNT(t)
            FROM ProductLiquorType t
            WHERE (:onlyConfirmed = false OR (t.recheckFlag = false AND t.suppressedFromTab = false))
            GROUP BY t.breweryId, t.liquorType
            ORDER BY t.breweryId, t.liquorType
            """)
    List<Object[]> aggregateByBreweryAndType(@Param("onlyConfirmed") boolean onlyConfirmed);
}
