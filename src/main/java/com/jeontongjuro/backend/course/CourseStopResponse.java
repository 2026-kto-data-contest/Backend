package com.jeontongjuro.backend.course;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** 지도 마커와 코스 상세 카드가 공통으로 사용하는 방문 장소. */
public record CourseStopResponse(
        int order,
        CourseStopType type,
        String contentId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distanceMeters,
        String imageUrl,
        String recommendationReason,
        String categoryName,
        String subcategoryName,
        String placeUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String pairingComment,
        List<String> featureTags,
        List<String> liquorTypes,
        String phone
) {
    public CourseStopResponse {
        featureTags = featureTags == null ? List.of() : List.copyOf(featureTags);
        liquorTypes = liquorTypes == null ? List.of() : List.copyOf(liquorTypes);
    }
}
