package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 장소 상세 조회 API. 목록({@link MapPlaceController})에서 고른 마커 한 건의 상세를 돌려준다.
 * <p>
 * ★{@code @Tag}는 목록 컨트롤러와 같은 "지도"를 쓴다 — 같은 화면·같은 자원의 목록/상세이므로 Swagger에서
 * 한 그룹으로 묶이는 편이 맞고, 이 저장소에는 이미 같은 도메인에 태그가 여러 벌 생긴 부채가 있다.
 */
@RestController
@RequestMapping("/api/v1/map/places")
@Tag(name = "지도", description = "지도 영역 기반 장소·마커 조회 API")
public class MapPlaceDetailController {

    private final MapPlaceDetailService mapPlaceDetailService;

    public MapPlaceDetailController(MapPlaceDetailService mapPlaceDetailService) {
        this.mapPlaceDetailService = mapPlaceDetailService;
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "지도 장소 상세 조회", description = """
            지도 마커 한 건의 상세 정보를 조회합니다.
            category가 조회 대상을 결정합니다 — BREWERY면 placeId를 양조장 ID(BRW-xxx)로,
            그 외에는 관광공사 content_id로 해석합니다.
            전통시장·문화시설은 목록과 동일하게 TOURIST_ATTRACTION으로 조회합니다.
            요청한 category와 실제 분류가 다르거나 목록에 노출되지 않는 장소면 404를 반환합니다.
            전화번호는 양조장에서만 제공되고, 거리(distanceMeters)는 이번 명세에서 항상 null입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "장소 상세 조회 성공"),
            @ApiResponse(responseCode = "400",
                    description = "category가 누락되었거나 허용된 5종 밖의 값(code=INVALID_QUERY_PARAMETER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_QUERY_PARAMETER\","
                                    + "\"message\":\"허용되지 않은 category 값입니다: 'FOOD' "
                                    + "(허용: BREWERY, RESTAURANT, TOURIST_ATTRACTION, CAFE, ACCOMMODATION)\"}"))),
            @ApiResponse(responseCode = "404",
                    description = "해당 placeId의 장소가 없거나, 요청 category와 실제 분류가 다르거나, "
                            + "목록에 노출되지 않는 장소(code=MAP_PLACE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"MAP_PLACE_NOT_FOUND\","
                                    + "\"message\":\"장소를 찾을 수 없습니다: 9999999\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MapPlaceDetailResponse detail(
            @Parameter(description = "장소 고유 ID. category=BREWERY면 BRW-xxx, 그 외는 관광공사 content_id",
                    example = "2788304")
            @PathVariable String placeId,
            @Parameter(description = "BREWERY, RESTAURANT, TOURIST_ATTRACTION, CAFE, ACCOMMODATION",
                    required = true, example = "RESTAURANT")
            @RequestParam(required = false) String category) {
        return mapPlaceDetailService.findDetail(placeId, category);
    }
}
