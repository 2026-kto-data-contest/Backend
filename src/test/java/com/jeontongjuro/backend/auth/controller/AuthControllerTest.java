package com.jeontongjuro.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.config.AppProperties;
import com.jeontongjuro.backend.auth.dto.response.LoginResult;
import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.auth.service.AuthService;
import com.jeontongjuro.backend.security.session.AuthCookieManager;
import com.jeontongjuro.backend.security.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    @Test
    void kakaoFailureReturnsBrowserToLoginScreen() throws Exception {
        AuthService authService = mock(AuthService.class);
        SessionService sessionService = mock(SessionService.class);
        AuthCookieManager cookieManager = mock(AuthCookieManager.class);
        AuthController controller = new AuthController(authService, sessionService, cookieManager,
                new AppProperties("http://localhost:3000"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(cookieManager.read(request, AuthCookieManager.OAUTH_STATE_COOKIE)).thenReturn("valid-state");
        when(cookieManager.readReturnTo(request)).thenReturn("/");
        when(authService.completeLogin("code", "/")).thenThrow(
                new AuthException(HttpStatus.BAD_GATEWAY, "KAKAO_AUTH_FAILED", "카카오 로그인 처리 실패"));

        controller.kakaoCallback("code", "valid-state", null, request, response);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/login?error=kakao_auth_failed");
        verify(cookieManager, never()).clearCsrfToken(response);
    }

    @Test
    void kakaoSuccessRotatesCsrfTokenBeforeIssuingSession() throws Exception {
        AuthService authService = mock(AuthService.class);
        SessionService sessionService = mock(SessionService.class);
        AuthCookieManager cookieManager = mock(AuthCookieManager.class);
        AuthController controller = new AuthController(authService, sessionService, cookieManager,
                new AppProperties("http://localhost:3000"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(cookieManager.read(request, AuthCookieManager.OAUTH_STATE_COOKIE)).thenReturn("valid-state");
        when(cookieManager.readReturnTo(request)).thenReturn("/");
        when(authService.completeLogin("code", "/"))
                .thenReturn(new LoginResult("new-session", "/terms"));
        when(sessionService.maxAgeSeconds()).thenReturn(1209600L);

        controller.kakaoCallback("code", "valid-state", null, request, response);

        verify(cookieManager).clearCsrfToken(response);
        verify(cookieManager).addSession(response, "new-session", 1209600L);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/terms");
    }

    @Test
    void logoutRevokesSessionAndClearsAuthenticationAndCsrfCookies() {
        AuthService authService = mock(AuthService.class);
        SessionService sessionService = mock(SessionService.class);
        AuthCookieManager cookieManager = mock(AuthCookieManager.class);
        AuthController controller = new AuthController(authService, sessionService, cookieManager,
                new AppProperties("http://localhost:3000"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieManager.read(request, AuthCookieManager.SESSION_COOKIE)).thenReturn("session-token");

        controller.logout(request, response);

        verify(sessionService).revoke("session-token");
        verify(cookieManager).clearSession(response);
        verify(cookieManager).clearCsrfToken(response);
    }
}
