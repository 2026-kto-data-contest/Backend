package com.jeontongjuro.backend.recommendation;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
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

/**
 * 추천 양조장 조회 API. GET /api/v1/recommendations/breweries.
 * <p>
 * 홈 「추천 양조장」 섹션, 검색 「이런 양조장은 어때요?」 그리드, 두 화면의 '더보기' 전체 목록이 공유하는
 * 단일 엔드포인트다. 공개 API지만({@link com.jeontongjuro.backend.security.config.SecurityConfig})
 * 로그인 사용자는 {@link AuthenticatedMember}가 주입돼 취향 기반 정렬이 적용된다(/api/v1/home과 동일한
 * 3-state 패턴).
 */
@RestController
@RequestMapping("/api/v1/recommendations/breweries")
@Tag(name = "추천", description = "추천 양조장 조회 API")
public class RecommendedBreweryController {

    private final RecommendedBreweryService recommendedBreweryService;

    public RecommendedBreweryController(RecommendedBreweryService recommendedBreweryService) {
        this.recommendedBreweryService = recommendedBreweryService;
    }

    @GetMapping
    @Operation(summary = "추천 양조장 조회", description = """
            홈 「추천 양조장」 섹션, 검색 「이런 양조장은 어때요?」 그리드, 두 화면의 '더보기' 전체 목록이
            공유하는 조회 API입니다. 비로그인 사용자도 조회할 수 있습니다.

            온보딩을 완료한 로그인 사용자는 취향(주종 &gt; 지역 우선순위) 기반 정렬 결과를 받고,
            비로그인이거나 온보딩 전이면 운영이 지정한 고정 순서를 받습니다. 두 경우 모두 필터가 아니라
            정렬이므로 전체 양조장이 결과에 포함됩니다(취향에 안 맞는 양조장도 뒤 순위로 포함되어 있습니다).

            결과 카드는 양조장 목록 API(GET /api/v1/breweries)와 동일한 형태입니다.
            page는 0부터 시작하며, 기본 size는 6(홈 섹션·검색 그리드 기준)입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 양조장 조회 성공"),
            @ApiResponse(responseCode = "500",
                    description = "서버 내부 오류(code=INTERNAL_SERVER_ERROR).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<BreweryListItemResponse> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "페이지 번호. 0부터 시작. 음수를 보내면 400이 아니라 0으로 보정됨", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 받을 개수. 기본 6, 최대 100. "
                    + "1 미만은 기본값(6), 100 초과는 100으로 보정됨", example = "6")
            @RequestParam(defaultValue = "6") int size) {
        return recommendedBreweryService.recommend(member == null ? null : member.id(), page, size);
    }
}
