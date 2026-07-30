package com.jeontongjuro.backend.pipeline.collect.raw;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 찾아가는양조장정보(dataset 15048756) RAW. 원본 7필드를 파싱·정제 없이 원문 그대로 보관한다.
 * 한글 키 ↔ 영문 컬럼 매핑 계약: docs/audit/20260729_raw_field_mapping.md
 * 유일한 비문자열 필드는 조회수(원본 JSON number → BIGINT). 그 외 전부 문자열 무손실.
 */
@Entity
@Table(name = "brewery_raw",
        uniqueConstraints = @UniqueConstraint(name = "uq_brewery_raw_snapshot_row",
                columnNames = {"snapshot_date", "source_row_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreweryRaw extends AbstractRawRow {

    @JsonProperty("사용여부")
    @Column(name = "use_yn", columnDefinition = "text")
    private String useYn;

    @JsonProperty("상시방문가능여부")
    @Column(name = "anytime_visit_yn", columnDefinition = "text")
    private String anytimeVisitYn;

    @JsonProperty("상호명")
    @Column(name = "business_name", columnDefinition = "text")
    private String businessName;

    @JsonProperty("예약방문가능여부")
    @Column(name = "reservation_visit_yn", columnDefinition = "text")
    private String reservationVisitYn;

    /** 원본이 JSON number인 유일한 필드 — 원본 타입 유지. */
    @JsonProperty("조회수")
    @Column(name = "view_count")
    private Long viewCount;

    @JsonProperty("주소")
    @Column(name = "address", columnDefinition = "text")
    private String address;

    @JsonProperty("홈페이지")
    @Column(name = "homepage_url", columnDefinition = "text")
    private String homepageUrl;
}
