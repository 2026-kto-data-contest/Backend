package com.jeontongjuro.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.kakao.KakaoClient;
import com.jeontongjuro.backend.auth.kakao.KakaoProperties;
import com.jeontongjuro.backend.auth.kakao.KakaoUserResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.security.session.SessionService;
import com.jeontongjuro.backend.terms.TermsService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private KakaoClient kakaoClient;
    private MemberRepository memberRepository;
    private SessionService sessionService;
    private TermsService termsService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        kakaoClient = mock(KakaoClient.class);
        memberRepository = mock(MemberRepository.class);
        sessionService = mock(SessionService.class);
        termsService = mock(TermsService.class);
        authService = new AuthService(kakaoClient,
                new KakaoProperties("rest-key", "client-secret", "http://localhost:8080/callback"),
                memberRepository, sessionService, termsService);
    }

    @Test
    void authorizationUrlContainsStateAndRegisteredRedirectUri() {
        var login = authService.startLogin();

        assertThat(login.state()).isNotBlank();
        assertThat(login.authorizationUrl())
                .startsWith("https://kauth.kakao.com/oauth/authorize")
                .contains("client_id=rest-key")
                .contains("response_type=code")
                .contains("state=" + login.state());
    }

    @Test
    void newMemberWithoutTermsMovesToTerms() {
        KakaoUserResponse user = new KakaoUserResponse(123L,
                new KakaoUserResponse.KakaoAccount(new KakaoUserResponse.Profile("전통주러버"), "user@example.com"));
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(kakaoClient.getUser("code")).thenReturn(user);
        when(memberRepository.findByKakaoUserId(123L)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);
        when(termsService.hasRequiredAgreements(10L)).thenReturn(false);
        when(sessionService.create(member)).thenReturn("session-token");

        var result = authService.completeLogin("code", "/breweries");

        assertThat(result.sessionToken()).isEqualTo("session-token");
        assertThat(result.nextPath()).isEqualTo("/terms");
    }

    @Test
    void openRedirectIsRejectedForCompletedMember() {
        KakaoUserResponse user = new KakaoUserResponse(123L,
                new KakaoUserResponse.KakaoAccount(new KakaoUserResponse.Profile("전통주러버"), null));
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(member.isOnboardingCompleted()).thenReturn(true);
        when(kakaoClient.getUser("code")).thenReturn(user);
        when(memberRepository.findByKakaoUserId(123L)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);
        when(termsService.hasRequiredAgreements(10L)).thenReturn(true);

        var result = authService.completeLogin("code", "//evil.example");

        assertThat(result.nextPath()).isEqualTo("/");
    }
}
