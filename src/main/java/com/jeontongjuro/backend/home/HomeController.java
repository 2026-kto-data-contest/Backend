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
            추천 코스는 최대 5개, 추천 양조장은 최대 6개이며 각각의 전체 추천 목록과 같은 순서를 사용합니다.
            온보딩 완료 회원은 저장된 주종·지역 취향이 추천 순서에 반영됩니다.
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
