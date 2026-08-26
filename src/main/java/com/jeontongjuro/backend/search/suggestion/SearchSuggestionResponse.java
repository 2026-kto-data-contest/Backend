package com.jeontongjuro.backend.search.suggestion;

import com.jeontongjuro.backend.search.recent.RecentSearchType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 자동완성 항목 응답. 필드 구성을 {@code RecentSearchSaveRequest}(type·id·keyword·displayName)와
 * 1:1로 맞춰, 항목을 탭했을 때 그대로 {@code POST /api/v1/search/recent} 요청 바디로 전달할 수 있게 한다.
 * <p>
 * keyword·displayName은 항상 이 항목의 표시 텍스트로 서로 같다(사용자가 입력한 원문 검색어가 아니다) —
 * 최근 검색에 저장되는 검색어는 "선택한 항목의 표시 텍스트"라는 기획 규약을 따른다.
 */
public record SearchSuggestionResponse(
        @Schema(description = "검색 대상 유형", example = "BREWERY", requiredMode = Schema.RequiredMode.REQUIRED)
        RecentSearchType type,
        @Schema(description = "검색 대상 식별자(양조장 BRW-xxx · 제품 PRD-xxxx, 병합 후 대표 제품 기준)",
                example = "BRW-001", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(description = "최근 검색 저장 시 사용할 검색어(이 항목의 표시 텍스트와 동일)", example = "산머루농원",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String keyword,
        @Schema(description = "화면에 표시할 텍스트(양조장명 또는 전통주명)", example = "산머루농원",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName) {
}
