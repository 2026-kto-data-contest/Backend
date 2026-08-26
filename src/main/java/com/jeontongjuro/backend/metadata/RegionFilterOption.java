package com.jeontongjuro.backend.metadata;

import com.jeontongjuro.backend.brewery.query.Region;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지역 필터 칩 하나(값 + 소재 양조장 수). 0건이어도 칩 자체는 포함해서 내려간다 — 노출 여부(0건이면
 * Chip 미생성) 판정은 프론트 몫이다({@link BreweryFilterMetadataResponse} 계약 참조).
 */
public record RegionFilterOption(
        @Schema(description = "지역 칩 값", example = "수도권",
                requiredMode = Schema.RequiredMode.REQUIRED) Region value,
        @Schema(description = "해당 지역에 소재한 양조장 수", example = "13",
                requiredMode = Schema.RequiredMode.REQUIRED) long breweryCount) {
}
