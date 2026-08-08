package com.jeontongjuro.backend.auth.controller;

import com.jeontongjuro.backend.auth.config.AppProperties;
import com.jeontongjuro.backend.auth.dto.response.MemberResponse;
import com.jeontongjuro.backend.auth.dto.response.NextPathResponse;
import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.auth.service.AuthService;
import com.jeontongjuro.backend.security.session.AuthCookieManager;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import com.jeontongjuro.backend.security.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "인증", description = "카카오 로그인 및 세션 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final AuthCookieManager cookieManager;
    private final AppProperties appProperties;

    @Operation(
            summary = "카카오 로그인 시작",
            description = """
                    카카오 로그인 버튼을 눌렀을 때 호출하는 주소입니다.

                    이 API는 JSON을 받는 API가 아니라 카카오 로그인 화면으로 이동시키는 API이므로,
                    fetch나 Axios로 요청하지 말고 window.location.href 또는 window.location.assign()을 사용하세요.

                    로그인 성공 후 이동:
                    필수 약관 미동의 회원은 /terms로 이동합니다.
                    온보딩 미완료 회원은 /onboarding으로 이동합니다.
                    모두 완료한 회원은 returnTo로 전달한 서비스 내부 경로로 이동합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "카카오 인증 화면으로 이동"),
            @ApiResponse(responseCode = "503", description = "카카오 환경설정 누락")
    })
    @GetMapping("/kakao")
    public void kakaoLogin(
                           @Parameter(description = "약관과 온보딩을 완료한 회원이 로그인 후 돌아갈 서비스 내부 경로",
                                   example = "/mypage")
                           @RequestParam(defaultValue = "/") String returnTo,
                           HttpServletResponse response) throws IOException {
        AuthService.LoginStart login = authService.startLogin();
        cookieManager.addOAuthState(response, login.state());
        cookieManager.addReturnTo(response, safeReturnTo(returnTo));
        response.sendRedirect(login.authorizationUrl());
    }

    @Hidden
    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam(required = false) String code,
                              @RequestParam(required = false) String state,
                              @RequestParam(required = false) String error,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        String expectedState = cookieManager.read(request, AuthCookieManager.OAUTH_STATE_COOKIE);
        String returnTo = cookieManager.readReturnTo(request);
        cookieManager.clearOAuthCookies(response);

        if (error != null) {
            response.sendRedirect(loginErrorUrl("kakao_cancelled"));
            return;
        }
        if (code == null || !sameValue(expectedState, state)) {
            response.sendRedirect(loginErrorUrl("invalid_oauth_state"));
            return;
        }

        try {
            var result = authService.completeLogin(code, returnTo);
            cookieManager.addSession(response, result.sessionToken(), sessionService.maxAgeSeconds());
            response.sendRedirect(appProperties.frontendUrl(result.nextPath()));
        } catch (AuthException exception) {
            response.sendRedirect(loginErrorUrl("kakao_auth_failed"));
        }
    }

    @Operation(
            summary = "현재 로그인 회원 조회",
            description = """
                    앱을 처음 열거나 새로고침했을 때 현재 로그인 상태와 회원 정보를 확인하는 API입니다.

                    로그인 쿠키는 JavaScript에서 직접 읽을 수 없는 HttpOnly 쿠키이므로 요청할 때
                    fetch는 credentials 옵션을 include로, Axios는 withCredentials 옵션을 true로 설정하세요.

                    응답 처리:
                    200이면 로그인 상태입니다. termsAgreed와 onboardingCompleted로 다음 화면을 결정하세요.
                    401이면 로그인하지 않았거나 세션이 만료된 상태이므로 로그인 화면으로 이동하세요.
                    """
    )
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 회원 조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal AuthenticatedMember member) {
        return authService.me(member.id());
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    현재 로그인 세션을 종료하고 로그인 쿠키를 삭제합니다.

                    먼저 GET /api/v1/auth/csrf를 호출한 뒤, 받은 토큰을 X-XSRF-TOKEN 헤더에 넣으세요.
                    fetch는 credentials 옵션을 include로, Axios는 withCredentials 옵션을 true로 설정하세요.
                    성공 응답은 내용이 없는 204이며, 프론트의 회원 상태를 비우고 로그인 화면으로 이동하면 됩니다.
                    """
    )
    @SecurityRequirement(name = "sessionCookie")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = cookieManager.read(request, AuthCookieManager.SESSION_COOKIE);
        sessionService.revoke(sessionToken);
        cookieManager.clearSession(response);
    }

    @Operation(
            summary = "로그인 후 다음 화면 결정",
            description = """
                    약관 저장 또는 온보딩 완료 후 호출하면 다음 이동 경로를 반환합니다.
                    필수 약관이 남아 있으면 /terms, 온보딩이 남아 있으면 /onboarding을 반환합니다.
                    모두 완료되면 로그인 직전의 안전한 서비스 내부 경로를 한 번 반환하고 삭제합니다.
                    """)
    @SecurityRequirement(name = "sessionCookie")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @PostMapping("/continue")
    public NextPathResponse continueLogin(@AuthenticationPrincipal AuthenticatedMember member) {
        return new NextPathResponse(authService.continueLogin(member.id()));
    }

    private boolean sameValue(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String loginErrorUrl(String errorCode) {
        return UriComponentsBuilder.fromUriString(appProperties.frontendUrl("/login"))
                .queryParam("error", errorCode)
                .build()
                .toUriString();
    }

    private String safeReturnTo(String value) {
        return value != null && value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")
                ? value : "/";
    }
}
