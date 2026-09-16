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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지도 화면의 수상 전통주 카드 조회 API. */
@RestController
@RequestMapping("/api/v1/map/awarded-liquors")
@Tag(name = "지도", description = "지도 추천 콘텐츠 API")
public class MapAwardedLiquorController {

    private final MapAwardedLiquorService service;

    public MapAwardedLiquorController(MapAwardedLiquorService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "지도 수상 전통주 조회", description = ""
            + "수상 이력이 있는 표시 전통주를 조회합니다. 판매중단·원본오류·중복 병합 제외 규칙은 "
            + "기존 제품 조회 API와 동일하게 적용하며, 제품이 속한 양조장의 주소와 좌표를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수상 전통주 조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapAwardedLiquorResponse> list(
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 기본 4, 최대 100", example = "4")
            @RequestParam(defaultValue = "4") int size) {
        return service.list(page, size);
    }
}
