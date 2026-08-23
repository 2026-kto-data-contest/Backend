package com.jeontongjuro.backend.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HomeBannerResponse(
        @Schema(description = "배너 유형") HomeBannerType type,
        @Schema(description = "배너 문구") String message,
        @Schema(description = "배너 탭 시 이동할 앱 내부 경로. 이동할 곳이 없으면 null", nullable = true)
        String actionPath
) {
}
