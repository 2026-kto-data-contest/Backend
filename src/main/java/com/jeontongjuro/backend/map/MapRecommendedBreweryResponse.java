package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.query.MainImageResponse;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 지도 추천 양조장 카드에 필요한 표시 정보와 지도 좌표를 함께 담는 응답 DTO. */
public record MapRecommendedBreweryResponse(
        @Schema(example = "BRW-001") String breweryId,
        @Schema(example = "해창주조장") String businessName,
        @Schema(example = "전라남도 해남군 화산면 해창길 1") String address,
        @Schema(example = "34.573210") BigDecimal latitude,
        @Schema(example = "126.598120") BigDecimal longitude,
        List<LiquorType> liquorTypes,
        List<FeatureType> featureTags,
        MainImageResponse mainImage) {
}
