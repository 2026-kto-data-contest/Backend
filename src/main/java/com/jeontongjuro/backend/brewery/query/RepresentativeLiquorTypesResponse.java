package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 양조장 상세 요약 카드에 표시할 대표주종과 생략된 주종 개수. */
public record RepresentativeLiquorTypesResponse(
        @Schema(description = "대표주종. 전체 주종이 3종 이상이면 우선순위 상위 2종",
                example = "[\"탁주\",\"증류주\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LiquorType> items,
        @Schema(description = "대표주종 뒤에 '외 N'으로 표시할 나머지 주종 개수", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int remainingCount
) {
    public RepresentativeLiquorTypesResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
