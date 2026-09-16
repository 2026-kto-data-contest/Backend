package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지도 화면의 추천 양조장 카드 조회 API. */
@RestController
@RequestMapping("/api/v1/map/recommended-breweries")
@Tag(name = "지도", description = "지도 추천 콘텐츠 API")
public class MapRecommendedBreweryController {

    private final MapRecommendedBreweryService service;

    public MapRecommendedBreweryController(MapRecommendedBreweryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "지도 추천 양조장 조회", description = ""
            + "지도 화면의 '전통주로에서 추천하는 양조장' 카드 목록을 조회합니다. "
            + "비로그인 사용자는 운영 지정 순서, 온보딩을 완료한 로그인 사용자는 취향 기반 순서를 받습니다. "
            + "기본 4개이며 추천 양조장 API와 동일한 페이지 규칙을 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 양조장 조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapRecommendedBreweryResponse> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 기본 4, 추천 API 최대값까지 허용", example = "4")
            @RequestParam(defaultValue = "4") int size) {
        return service.recommend(member == null ? null : member.id(), page, size);
    }
}
