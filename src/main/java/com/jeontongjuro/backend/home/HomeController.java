package com.jeontongjuro.backend.home;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import com.jeontongjuro.backend.home.dto.HomeResponse;
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

@Tag(name = "홈", description = "홈 화면 조회 API")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 화면 조회", description = """
            홈 화면의 헤더, 배너, 추천 코스, 주종별·지역별·추천 양조장 데이터를 한 번에 조회합니다.
            비로그인 사용자도 조회할 수 있으며, region과 liquorType의 기본값은 각각 수도권과 탁주입니다.
            현재 추천 코스는 빈 배열, 추천 양조장은 고정 정렬 기본 목록으로 반환합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "홈 화면 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 지역 또는 주종",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public HomeResponse getHome(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "지역 필터. 미지정 시 수도권", example = "전라")
            @RequestParam(required = false) String region,
            @Parameter(description = "주종 필터. 미지정 시 탁주", example = "탁주")
            @RequestParam(required = false) String liquorType) {
        return homeService.getHome(member == null ? null : member.id(), region, liquorType);
    }
}
