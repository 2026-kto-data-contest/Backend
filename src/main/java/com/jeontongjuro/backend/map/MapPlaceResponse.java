package com.jeontongjuro.backend.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record MapPlaceResponse(
        @Schema(example = "BRW-001") String placeId,
        @Schema(example = "해창주조장") String placeName,
        MapPlaceCategory category,
        @Schema(example = "양조장") String categoryName,
        @Schema(description = "거리 계산은 클라이언트에서 수행하며 서버 응답에서는 항상 null", example = "1.2")
        Double distance,
        @Schema(example = "전라남도 해남군 화산면 해창길 1") String roadAddressName,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl) {
}
