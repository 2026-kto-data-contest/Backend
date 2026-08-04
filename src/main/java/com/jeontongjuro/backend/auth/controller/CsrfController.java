package com.jeontongjuro.backend.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "카카오 로그인 및 세션 API")
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @Operation(
            summary = "CSRF 토큰 발급",
            description = """
                    쿠키 로그인 상태에서 POST·PATCH·DELETE처럼 데이터를 변경하는 요청을 안전하게 보내기 위한 토큰입니다.
                    로그인 토큰이나 카카오 토큰이 아니며, 프론트가 따로 저장해 두는 인증 정보도 아닙니다.

                    사용 순서:
                    1. fetch는 credentials 옵션을 include로, Axios는 withCredentials 옵션을 true로 설정해 호출합니다.
                    2. 응답의 headerName과 token 값을 확인합니다.
                    3. 바로 다음 상태 변경 요청에 headerName을 이름으로, token을 값으로 하는 헤더를 추가합니다.

                    예를 들어 headerName이 X-XSRF-TOKEN이면 다음 요청의 X-XSRF-TOKEN 헤더에 token 값을 넣습니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "CSRF 토큰 발급 성공")
    @GetMapping("/csrf")
    public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    public record CsrfResponse(
            @Schema(description = "상태 변경 요청에 사용할 헤더 이름", example = "X-XSRF-TOKEN") String headerName,
            @Schema(description = "상태 변경 요청 헤더에 담을 CSRF 토큰") String token
    ) {
    }
}
