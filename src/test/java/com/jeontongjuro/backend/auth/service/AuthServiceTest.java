package com.jeontongjuro.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import com.jeontongjuro.backend.auth.kakao.KakaoClient;
import com.jeontongjuro.backend.auth.config.AppProperties;
import com.jeontongjuro.backend.auth.kakao.KakaoProperties;
import com.jeontongjuro.backend.auth.kakao.KakaoUserResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.security.session.SessionService;
import com.jeontongjuro.backend.terms.TermsService;
import java.util.Optional;
import java.util.List;
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
                new KakaoProperties("rest-key", "client-secret", "http://localhost:8080/callback", "admin-key"),
                new AppProperties("http://localhost:5173", List.of("http://localhost:5173")),
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
        verify(member).rememberPostLoginReturnTo("/breweries");
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

    @Test
    void continueLoginKeepsOriginalPathUntilAllStepsAreComplete() {
        Member member = mock(Member.class);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
        when(termsService.hasRequiredAgreements(10L)).thenReturn(true);
        when(member.isOnboardingCompleted()).thenReturn(false);

        assertThat(authService.continueLogin(10L)).isEqualTo("/onboarding");

        when(member.isOnboardingCompleted()).thenReturn(true);
        when(member.consumePostLoginReturnTo()).thenReturn("/breweries/BRW-001");

        assertThat(authService.continueLogin(10L)).isEqualTo("/breweries/BRW-001");
    }

    @Test
    void withdrawDeletesMemberAccount() {
        Member member = mock(Member.class);
        when(member.getKakaoUserId()).thenReturn(123L);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        authService.withdraw(10L);

        verify(memberRepository).delete(member);
        verify(kakaoClient).unlink(123L);
    }

    @Test
    void withdrawnKakaoAccountCanRegisterAgainWithTheSameKakaoUserId() {
        KakaoUserResponse user = new KakaoUserResponse(123L,
                new KakaoUserResponse.KakaoAccount(
                        new KakaoUserResponse.Profile("재가입 사용자"), "rejoin@example.com"));
        Member recreatedMember = mock(Member.class);
        when(recreatedMember.getId()).thenReturn(11L);
        when(kakaoClient.getUser("code")).thenReturn(user);
        when(memberRepository.findByKakaoUserId(123L)).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenReturn(recreatedMember);
        when(termsService.hasRequiredAgreements(11L)).thenReturn(false);
        when(sessionService.create(recreatedMember)).thenReturn("rejoin-session");

        var result = authService.completeLogin("code", "/");

        assertThat(result.sessionToken()).isEqualTo("rejoin-session");
        assertThat(result.nextPath()).isEqualTo("/terms");
        var recreated = org.mockito.ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, org.mockito.Mockito.atLeastOnce()).save(recreated.capture());
        assertThat(recreated.getAllValues().get(0).getKakaoUserId()).isEqualTo(123L);
    }
}
