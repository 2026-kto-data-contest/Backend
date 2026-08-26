package com.jeontongjuro.backend.metadata;

import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주종 필터 칩 하나(값 + 취급 양조장 수). 0건이어도 칩 자체는 포함해서 내려간다 — 노출 여부(0건이면
 * Chip 미생성) 판정은 프론트 몫이다({@link BreweryFilterMetadataResponse} 계약 참조).
 */
public record LiquorTypeFilterOption(
        @Schema(description = "주종 값", example = "탁주",
                requiredMode = Schema.RequiredMode.REQUIRED) LiquorType value,
        @Schema(description = "해당 주종을 취급하는 양조장 수. 0일 수 있다(예: 기타)", example = "32",
                requiredMode = Schema.RequiredMode.REQUIRED) long breweryCount) {
}
