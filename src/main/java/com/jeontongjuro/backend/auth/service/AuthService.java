package com.jeontongjuro.backend.auth.service;

import com.jeontongjuro.backend.auth.dto.response.LoginResult;
import com.jeontongjuro.backend.auth.config.AppProperties;
import com.jeontongjuro.backend.auth.dto.response.MemberResponse;
import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.auth.kakao.KakaoClient;
import com.jeontongjuro.backend.auth.kakao.KakaoProperties;
import com.jeontongjuro.backend.auth.kakao.KakaoUserResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.security.session.SessionService;
import com.jeontongjuro.backend.terms.TermsService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;
    private final AppProperties appProperties;
    private final MemberRepository memberRepository;
    private final SessionService sessionService;
    private final TermsService termsService;

    public LoginStart startLogin() {
        kakaoProperties.validateConfigured();
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String authorizationUrl = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("client_id", kakaoProperties.restApiKey())
                .queryParam("redirect_uri", kakaoProperties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        return new LoginStart(state, authorizationUrl);
    }

    @Transactional
    public LoginResult completeLogin(String authorizationCode, String returnTo) {
        KakaoUserResponse kakaoUser = kakaoClient.getUser(authorizationCode);
        UpsertedMember upsertedMember = upsertMember(kakaoUser);
        Member member = upsertedMember.member();
        member.rememberPostLoginReturnTo(appProperties.safeReturnTo(returnTo));
        memberRepository.save(member);
        boolean termsAgreed = termsService.hasRequiredAgreements(member.getId());
        String sessionToken = sessionService.create(member);
        String nextPath = termsAgreed
                ? (upsertedMember.created()
                        ? "/onboarding"
                        : appProperties.safeReturnTo(member.consumePostLoginReturnTo()))
                : "/terms";
        memberRepository.save(member);
        return new LoginResult(sessionToken, nextPath);
    }

    @Transactional
    public String continueLogin(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND",
                        "회원 정보를 찾을 수 없습니다."));
        if (!termsService.hasRequiredAgreements(memberId)) {
            return "/terms";
        }
        if (!member.isOnboardingCompleted()) {
            return "/onboarding";
        }
        String returnTo = appProperties.safeReturnTo(member.consumePostLoginReturnTo());
        memberRepository.save(member);
        return returnTo;
    }

    @Transactional(readOnly = true)
    public MemberResponse me(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        return MemberResponse.from(member, termsService.hasRequiredAgreements(memberId));
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND",
                        "회원 정보를 찾을 수 없습니다."));
        kakaoClient.unlink(member.getKakaoUserId());
        memberRepository.delete(member);
    }

    @Transactional
    protected UpsertedMember upsertMember(KakaoUserResponse kakaoUser) {
        String nickname = kakaoUser.nickname() == null || kakaoUser.nickname().isBlank()
                ? "카카오 사용자" : kakaoUser.nickname();
        OptionalMember existing = memberRepository.findByKakaoUserId(kakaoUser.id())
                .map(member -> new OptionalMember(member, false))
                .orElseGet(() -> new OptionalMember(
                        Member.createKakao(kakaoUser.id(), nickname, kakaoUser.email()), true));
        Member member = existing.member();
        member.updateKakaoProfile(nickname, kakaoUser.email());
        return new UpsertedMember(memberRepository.save(member), existing.created());
    }

    private record OptionalMember(Member member, boolean created) {
    }

    record UpsertedMember(Member member, boolean created) {
    }

    public record LoginStart(String state, String authorizationUrl) {
    }
}
