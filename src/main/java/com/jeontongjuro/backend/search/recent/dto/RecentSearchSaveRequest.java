package com.jeontongjuro.backend.search.recent.dto;

import com.jeontongjuro.backend.search.recent.RecentSearchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecentSearchSaveRequest(
        @NotNull RecentSearchType type,
        @NotBlank @Size(max = 32) String id,
        @NotBlank @Size(max = 20) String keyword,
        @NotBlank @Size(max = 100) String displayName
) {
}
