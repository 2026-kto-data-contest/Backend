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
public class MapPlaceController {
    private final MapPlaceService mapPlaceService;

    public MapPlaceController(MapPlaceService mapPlaceService) {
        this.mapPlaceService = mapPlaceService;
    }

    @GetMapping
    @Operation(summary = "지도 영역의 카테고리별 장소 조회", description = """
            현재 지도 영역 안의 양조장·식당·관광지·카페·숙소를 조회합니다.
            사용자 좌표를 함께 보내면 거리순, 보내지 않으면 장소명순으로 반환합니다.
            전통시장은 관광지에 포함되며, 좌표가 없는 장소는 노출하지 않습니다.
            page는 0부터 시작하고 size는 최대 300으로 보정됩니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "장소 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 지도 영역·카테고리·사용자 좌표",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapPlaceResponse> places(
            @Parameter(description = "지도 남쪽 경계 위도", example = "33.0")
            @RequestParam BigDecimal south, @RequestParam BigDecimal west,
            @RequestParam BigDecimal north, @RequestParam BigDecimal east,
            @Parameter(description = "BREWERY, RESTAURANT, TOURIST_ATTRACTION, CAFE, ACCOMMODATION",
                    example = "BREWERY")
            @RequestParam String category,
            @RequestParam(required = false) BigDecimal userLatitude,
            @RequestParam(required = false) BigDecimal userLongitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mapPlaceService.find(south, west, north, east, category,
                userLatitude, userLongitude, page, size);
    }
}
