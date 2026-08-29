package com.jeontongjuro.backend.recommendation;

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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations/courses")
@RequiredArgsConstructor
@Tag(name = "추천", description = "추천 코스 조회 API")
public class RecommendedCourseListController {

    private final RecommendedCourseListService recommendedCourseListService;

    @GetMapping
    @Operation(summary = "추천 코스 전체 조회", description = """
            홈 추천 코스 더보기에서 사용하는 공개 목록 API입니다. 코스 ID는 중심 양조장 ID와 같아서
            GET /api/v1/breweries/{breweryId}/recommended-course 상세 조회에 그대로 사용할 수 있습니다.
            비로그인·온보딩 전에는 고정 순서, 온보딩 완료 회원은 주종·지역 취향 우선 순서로 반환합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 코스 전체 조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<RecommendedCourseCardResponse> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "페이지 번호. 음수는 0으로 보정", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 기본 20, 1 미만은 20, 최대 100", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return recommendedCourseListService.list(member == null ? null : member.id(), page, size);
    }
}
