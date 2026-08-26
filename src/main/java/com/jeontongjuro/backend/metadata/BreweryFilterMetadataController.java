package com.jeontongjuro.backend.metadata;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색·목록 필터 조회 API. GET /api/v1/metadata/brewery-filters.
 * <p>
 * 홈 화면 주종·지역 필터 칩에 필요한 메타데이터(값 + 양조장 수)를 무인자로 조회한다.
 * 파라미터가 없다 — 이 엔드포인트는 필터를 적용하는 게 아니라 필터 칩 자체를 그리는 데 필요한 개수를 내려준다.
 */
@RestController
@RequestMapping("/api/v1/metadata")
@Tag(name = "메타데이터", description = "검색·목록 필터 메타데이터 조회 API")
public class BreweryFilterMetadataController {

    private final BreweryFilterMetadataService breweryFilterMetadataService;

    public BreweryFilterMetadataController(BreweryFilterMetadataService breweryFilterMetadataService) {
        this.breweryFilterMetadataService = breweryFilterMetadataService;
    }

    @GetMapping("/brewery-filters")
    @Operation(summary = "양조장 필터 칩 메타데이터 조회", description = """
            홈 화면 주종·지역 필터 칩을 그리는 데 필요한 메타데이터입니다.
            주종 6종·지역 8종을 명세 나열 순서로 고정 반환하며, 각 칩에 해당 조건의 양조장 수를 포함합니다.

            0건인 칩도 breweryCount=0으로 그대로 내려갑니다 — "1건 이상일 때만 Chip 생성"은 화면 동작
            규칙이라 프론트가 판정합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500",
                    description = "서버 내부 오류(code=INTERNAL_SERVER_ERROR). 예기치 못한 예외를 공통 계약으로 감싼 응답.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\","
                                    + "\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    public BreweryFilterMetadataResponse breweryFilters() {
        return breweryFilterMetadataService.filters();
    }
}
