package com.jeontongjuro.backend.home.dto;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.recommendation.RecommendedCourseCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HomeResponse(
        HomeViewerResponse viewer,
        HomeHeaderResponse header,
        HomeBannerResponse banner,
        @Schema(description = "추천 코스 카드. 최대 5개")
        List<RecommendedCourseCardResponse> recommendedCourses,
        HomeBrewerySectionResponse liquorTypeBreweries,
        HomeBrewerySectionResponse regionBreweries,
        @Schema(description = "추천 양조장 카드. 최대 6개")
        List<BreweryListItemResponse> recommendedBreweries
) {
}
