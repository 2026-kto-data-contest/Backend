package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map/places")
@Tag(name = "지도", description = "지도 영역 기반 장소·마커 조회 API")
public class MapPlaceSearchController {
    private final MapPlaceService mapPlaceService;

    public MapPlaceSearchController(MapPlaceService mapPlaceService) {
        this.mapPlaceService = mapPlaceService;
    }

    @GetMapping("/search")
    @Operation(summary = "지도 장소 검색", description = """
            장소명·주소로 양조장·식당·관광지·카페·숙소를 검색합니다.
            category를 생략하면 전체 카테고리를 검색합니다.
            latitude와 longitude를 함께 보내면 거리순, 생략하면 정확도순·장소명순으로 반환합니다.
            검색 결과의 placeId·category·좌표로 지도 중심을 이동하고 동일 마커를 선택할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공(결과가 없으면 빈 content)"),
            @ApiResponse(responseCode = "400", description = "잘못된 검색어·카테고리·사용자 좌표",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapPlaceResponse> search(
            @Parameter(description = "장소명·주소 검색어. 공백 제거 후 1~50자", example = "안동")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "BREWERY, RESTAURANT, TOURIST_ATTRACTION, CAFE, ACCOMMODATION")
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mapPlaceService.search(keyword, category, latitude, longitude, page, size);
    }
}
