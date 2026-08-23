package com.jeontongjuro.backend.home.dto;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HomeResponse(
        HomeViewerResponse viewer,
        HomeHeaderResponse header,
        HomeBannerResponse banner,
        @Schema(description = "추천 코스 카드. 미구현 상태에서는 빈 배열")
        List<HomeCourseCardResponse> recommendedCourses,
        HomeBrewerySectionResponse liquorTypeBreweries,
        HomeBrewerySectionResponse regionBreweries,
        @Schema(description = "추천 양조장 카드. 개인화 전에는 기본 고정 정렬 목록")
        List<BreweryListItemResponse> recommendedBreweries
) {
}
