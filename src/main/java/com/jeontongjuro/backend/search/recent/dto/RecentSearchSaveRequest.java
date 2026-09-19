package com.jeontongjuro.backend.search.recent.dto;

import com.jeontongjuro.backend.search.recent.RecentSearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecentSearchSaveRequest(
        @NotNull RecentSearchType type,
        @Schema(description = "BREWERY·PRODUCT·REGION은 대상 ID 필수, KEYWORD는 생략 가능", nullable = true,
                example = "BRW-001")
        @Size(max = 32) String id,
        @NotBlank @Size(max = 20) String keyword,
        @NotBlank @Size(max = 100) String displayName
) {
}
