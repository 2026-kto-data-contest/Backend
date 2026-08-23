package com.jeontongjuro.backend.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HomeViewerResponse(
        @Schema(description = "로그인 여부") boolean authenticated,
        @Schema(description = "취향 온보딩 완료 여부. 비로그인은 false") boolean onboardingCompleted
) {
}
