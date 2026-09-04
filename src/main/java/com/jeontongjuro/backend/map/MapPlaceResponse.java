package com.jeontongjuro.backend.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record MapPlaceResponse(
        @Schema(example = "BRW-001") String placeId,
        @Schema(example = "해창주조장") String placeName,
        MapPlaceCategory category,
        @Schema(example = "양조장") String categoryName,
        @Schema(description = "사용자 위치에서 장소까지의 직선거리(km). 사용자 좌표가 없으면 null", example = "1.2")
        Double distance,
        @Schema(example = "전라남도 해남군 화산면 해창길 1") String roadAddressName,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl) {
}
