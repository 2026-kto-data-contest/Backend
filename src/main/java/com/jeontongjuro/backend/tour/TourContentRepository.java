package com.jeontongjuro.backend.tour;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * TourAPI 콘텐츠 캐시 리포지토리. PK = contentid(String).
 */
public interface TourContentRepository extends JpaRepository<TourContent, String> {

    @Query("SELECT t FROM TourContent t WHERE t.latitude BETWEEN :south AND :north "
            + "AND t.longitude BETWEEN :west AND :east")
    List<TourContent> findWithinBounds(@Param("south") BigDecimal south, @Param("north") BigDecimal north,
                                       @Param("west") BigDecimal west, @Param("east") BigDecimal east);

    /** 조회 API 배치 로딩용 — 페이지 양조장의 content_id 집합으로 대표 이미지(first_image·cpyrht_div_cd)를 한 번에 읽는다. */
    List<TourContent> findByContentIdIn(Collection<String> contentIds);
}
