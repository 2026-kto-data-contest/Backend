package com.jeontongjuro.backend.liquortype;

import java.util.Collection;
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

    /**
     * 조회 API 배치 로딩용 — 페이지의 brewery_id 집합으로 취급 주종(distinct)을 한 번에 읽어 N+1을 피한다.
     * ★검수 상태(recheck_flag)·억제(suppressed_from_tab) 무관 전체 태깅을 대상으로 한다 — 이는 주종 필터
     * (?liquorType=)가 EXISTS로 recheck 무관하게 판정하는 것과 동일한 의미라, 필터로 걸린 양조장이 응답에서
     * 그 주종을 반드시 노출하도록 일치시킨다(육안 실측: AUTO 태깅도 제품명과 정합 확인).
     *
     * @return {@code [breweryId(String), liquorType(LiquorType)]} 튜플 목록(양조장·주종 조합별 1행)
     */
    @Query("""
            SELECT DISTINCT t.breweryId, t.liquorType
            FROM ProductLiquorType t
            WHERE t.breweryId IN :breweryIds
            """)
    List<Object[]> findDistinctTypesByBreweryIdIn(@Param("breweryIds") Collection<String> breweryIds);

    /**
     * 제품 카드 조회용 — 제품(source_row_ref)별 주종(distinct)을 IN 배치로 읽어 N+1을 피한다.
     * 한 제품이 주종을 여럿 가지면(2주종 실존) 조합별로 여러 행이 반환된다.
     * <p>
     * ★{@code suppressed_from_tab}은 <b>양조장 주종 탭 전용 재검토 플래그</b>이지 오태깅 표시가 아니다.
     * 제품 카드는 제품 단위 표시라 이 플래그를 적용하면 유일 주종이 사라진다. (해당 9건은 전부 제품당
     * 유일 주종이며 태깅 내용은 정확하다.) 따라서 여기서는 억제 필터를 걸지 않는다.
     *
     * @return {@code [sourceRowRef(Integer), liquorType(LiquorType)]} 튜플 목록(제품·주종 조합별 1행)
     */
    @Query("""
            SELECT DISTINCT t.sourceRowRef, t.liquorType
            FROM ProductLiquorType t
            WHERE t.sourceRowRef IN :sourceRowRefs
            """)
    List<Object[]> findTypesBySourceRowRefIn(@Param("sourceRowRefs") Collection<Integer> sourceRowRefs);

    /**
     * 주종별 취급 양조장 수(필터 메타데이터 조회용). {@code COUNT(DISTINCT breweryId)}로 세는 이유 =
     * 한 양조장이 같은 주종을 여러 제품으로 취급하면 여러 행이 나오기 때문(중복 제거 없이 세면 과대집계).
     * 5종 합이 59(브루어리 전체)가 아니라 108인 것도 이 다대다 특성 때문 — 정상.
     * 6종 GROUP BY 1쿼리라 주종별로 따로 세지 않는다. recheck_flag·suppressed_from_tab 무관 전체 태깅 대상
     * (조회 API의 취급 주종 노출·필터 판정과 동일 의미, {@link #findDistinctTypesByBreweryIdIn} 선례와 일치).
     *
     * @return {@code [liquorType(LiquorType), breweryCount(Long)]} 튜플 목록. 취급 브루어리가 0인 주종
     *         (예: 기타)은 GROUP BY 특성상 이 목록에 아예 나타나지 않는다 — 호출자가 0으로 채워야 한다.
     */
    @Query("SELECT t.liquorType, COUNT(DISTINCT t.breweryId) FROM ProductLiquorType t GROUP BY t.liquorType")
    List<Object[]> countDistinctBreweriesByLiquorType();
}
