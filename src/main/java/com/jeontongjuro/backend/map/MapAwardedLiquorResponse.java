package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.brewery.query.MainImageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 지도 수상 전통주 카드와 해당 양조장의 위치 정보를 담는 응답 DTO. */
public record MapAwardedLiquorResponse(
        @Schema(example = "441") Integer productId,
        @Schema(example = "우곡생주") String productName,
        @Schema(example = "BRW-001") String breweryId,
        @Schema(example = "해창주조장") String breweryName,
        @Schema(example = "대상") String awardBadge,
        List<LiquorType> liquorTypes,
        BigDecimal alcoholMin,
        BigDecimal alcoholMax,
        String volume,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        MainImageResponse image) {

    /** 이미지 필드 추가 전 호출부와의 호환용 생성자. */
    public MapAwardedLiquorResponse(Integer productId, String productName, String breweryId, String breweryName,
                                    String awardBadge, List<LiquorType> liquorTypes, BigDecimal alcoholMin,
                                    BigDecimal alcoholMax, String volume, String address, BigDecimal latitude,
                                    BigDecimal longitude) {
        this(productId, productName, breweryId, breweryName, awardBadge, liquorTypes, alcoholMin, alcoholMax,
                volume, address, latitude, longitude, null);
    }
}
