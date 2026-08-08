package com.jeontongjuro.backend.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NextPathResponse(
        @Schema(description = "프론트가 이동할 서비스 내부 경로", example = "/breweries/BRW-001")
        String nextPath
) {
}
