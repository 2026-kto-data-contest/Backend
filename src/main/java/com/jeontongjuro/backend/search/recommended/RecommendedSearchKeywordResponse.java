package com.jeontongjuro.backend.search.recommended;

import io.swagger.v3.oas.annotations.media.Schema;

/** 검색 화면에 노출할 서버 관리 추천 검색어. */
public record RecommendedSearchKeywordResponse(
        @Schema(description = "추천 검색어", example = "막걸리") String keyword,
        @Schema(description = "해당 검색어의 양조장 검색 결과 수", example = "20") int resultCount) {
}
