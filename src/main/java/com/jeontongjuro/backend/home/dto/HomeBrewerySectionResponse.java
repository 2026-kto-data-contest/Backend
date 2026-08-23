package com.jeontongjuro.backend.home.dto;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HomeBrewerySectionResponse(
        @Schema(description = "현재 선택된 주종 또는 지역 값") String selectedValue,
        @Schema(description = "섹션에 노출할 양조장 카드 목록") List<BreweryListItemResponse> breweries
) {
}
