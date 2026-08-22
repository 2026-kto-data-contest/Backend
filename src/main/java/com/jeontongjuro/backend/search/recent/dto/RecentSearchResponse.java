package com.jeontongjuro.backend.search.recent.dto;

import com.jeontongjuro.backend.search.recent.RecentSearch;
import com.jeontongjuro.backend.search.recent.RecentSearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record RecentSearchResponse(
        @Schema(description = "최근 검색 기록 ID", example = "1") Long recentSearchId,
        @Schema(description = "검색 대상 유형", example = "BREWERY") RecentSearchType type,
        @Schema(description = "검색 대상 식별자", example = "BRW-001") String id,
        @Schema(description = "사용자가 입력한 검색어", example = "갈기산") String keyword,
        @Schema(description = "화면 표시명", example = "갈기산") String displayName,
        @Schema(description = "마지막 검색 시각(UTC)") LocalDateTime searchedAt
) {
    public static RecentSearchResponse from(RecentSearch recentSearch) {
        return new RecentSearchResponse(
                recentSearch.getId(),
                recentSearch.getType(),
                recentSearch.getTargetId(),
                recentSearch.getKeyword(),
                recentSearch.getDisplayName(),
                recentSearch.getSearchedAt());
    }
}
