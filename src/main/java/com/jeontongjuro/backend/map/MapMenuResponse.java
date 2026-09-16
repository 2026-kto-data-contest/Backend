package com.jeontongjuro.backend.map;

import io.swagger.v3.oas.annotations.media.Schema;

/** 지도 메뉴 필터 버튼에 표시할 메뉴 항목. */
public record MapMenuResponse(
        @Schema(example = "PAJEON") String menu,
        @Schema(example = "파전") String displayName) {
}
