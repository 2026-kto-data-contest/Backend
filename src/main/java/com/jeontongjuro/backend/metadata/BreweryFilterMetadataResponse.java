package com.jeontongjuro.backend.metadata;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 화면 양조장 필터 칩(주종·지역) 메타데이터 응답. 각 배열은 명세 나열 순서로 고정된다:
 * 주종 = 탁주→약주→청주→증류주→과실주→기타, 지역 = 수도권→강원→충청→전라→경상→부산→울산→제주.
 * <p>
 * ★0건인 항목도 breweryCount=0으로 그대로 포함한다 — "1건 이상일 때만 Chip 생성"은 화면 동작 규칙이라
 * 프론트가 판정하고, 백엔드는 사실(개수)만 내린다. 필터 파라미터는 없다(무인자 조회, 전체 59 기준 독립 집계).
 */
public record BreweryFilterMetadataResponse(
        @Schema(description = "주종 필터 칩 6종(명세 순서 고정). 6종 항상 전부 포함",
                requiredMode = Schema.RequiredMode.REQUIRED) List<LiquorTypeFilterOption> liquorTypes,
        @Schema(description = "지역 필터 칩 8종(명세 순서 고정). 8종 항상 전부 포함",
                requiredMode = Schema.RequiredMode.REQUIRED) List<RegionFilterOption> regions) {
}
