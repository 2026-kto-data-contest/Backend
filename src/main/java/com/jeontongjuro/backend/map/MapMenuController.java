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
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지도 화면의 메뉴 필터와 메뉴별 장소 조회 API. */
@RestController
@RequestMapping("/api/v1/map/menus")
@Tag(name = "지도", description = "지도 추천 콘텐츠 API")
public class MapMenuController {
    private final MapMenuService service;

    public MapMenuController(MapMenuService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "지도 추천 메뉴 목록 조회", description = "지도에서 선택할 수 있는 추천 메뉴 카테고리를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "메뉴 목록 조회 성공")
    public List<MapMenuResponse> menus() {
        return service.menus();
    }

    @GetMapping("/{menu}/places")
    @Operation(summary = "메뉴별 지도 장소 조회", description = "선택한 메뉴와 관련된 음식점 장소를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 장소 조회 성공"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 메뉴 또는 잘못된 사용자 좌표",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapPlaceResponse> places(
            @Parameter(description = "메뉴 코드. GET /api/v1/map/menus 응답의 menu 값", example = "PAJEON")
            @PathVariable String menu,
            @Parameter(description = "사용자 위치 위도", example = "37.5665")
            @RequestParam(required = false) BigDecimal userLatitude,
            @Parameter(description = "사용자 위치 경도", example = "126.9780")
            @RequestParam(required = false) BigDecimal userLongitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.places(menu, userLatitude, userLongitude, page, size);
    }
}
