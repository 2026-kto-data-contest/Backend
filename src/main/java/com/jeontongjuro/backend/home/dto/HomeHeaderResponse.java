package com.jeontongjuro.backend.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HomeHeaderResponse(
        @Schema(description = "사용자 상태에 맞춘 홈 헤더 문구") String message
) {
}
