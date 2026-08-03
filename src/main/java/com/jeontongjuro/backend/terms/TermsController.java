package com.jeontongjuro.backend.terms;

import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import com.jeontongjuro.backend.terms.dto.TermsAgreementRequest;
import com.jeontongjuro.backend.terms.dto.TermsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "약관", description = "서비스 약관 조회 및 동의 API")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @Operation(
            summary = "현재 약관 조회",
            description = """
                    약관 동의 화면에 들어왔을 때 호출합니다.
                    화면에 표시할 순서대로 약관명, 필수 여부, 전문 URL, 현재 회원의 동의 여부를 반환합니다.

                    required가 true이면 반드시 동의해야 하는 약관이고, agreed는 현재 저장된 동의 상태입니다.
                    fetch는 credentials 옵션을 include로, Axios는 withCredentials 옵션을 true로 설정하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "약관 및 동의 상태 조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping
    public List<TermsResponse> getTerms(@AuthenticationPrincipal AuthenticatedMember member) {
        return termsService.getCurrentTerms(member.id());
    }

    @Operation(
            summary = "약관 동의 저장",
            description = """
                    사용자가 약관 화면에서 선택한 결과를 저장할 때 호출합니다.

                    요청의 agreements에는 화면에 표시된 약관 코드를 각각 한 번씩 담으세요.
                    SERVICE_USE와 PRIVACY는 필수이므로 반드시 true여야 합니다.
                    LOCATION과 MARKETING은 선택 약관이므로 사용자의 선택에 따라 true 또는 false를 보냅니다.

                    호출 전 GET /api/v1/auth/csrf에서 받은 토큰을 X-XSRF-TOKEN 헤더에 넣으세요.
                    fetch는 credentials 옵션을 include로, Axios는 withCredentials 옵션을 true로 설정하세요.
                    성공하면 갱신된 약관 동의 목록을 반환하며, 프론트는 온보딩 화면으로 이동하면 됩니다.
                    """
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "약관 동의 저장 성공"),
            @ApiResponse(responseCode = "400", description = "필수 약관 미동의 또는 잘못된 약관 코드"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치")
    })
    @PostMapping("/agreements")
    public List<TermsResponse> agree(@AuthenticationPrincipal AuthenticatedMember member,
                                     @Valid @RequestBody TermsAgreementRequest request) {
        return termsService.agree(member.id(), request);
    }
}
