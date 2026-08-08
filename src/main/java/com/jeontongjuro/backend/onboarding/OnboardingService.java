package com.jeontongjuro.backend.onboarding;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.terms.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final MemberRepository memberRepository;
    private final TermsService termsService;

    @Transactional
    public void complete(Long memberId) {
        if (!termsService.hasRequiredAgreements(memberId)) {
            throw new AuthException(
                    HttpStatus.CONFLICT,
                    "TERMS_AGREEMENT_REQUIRED",
                    "필수 약관 동의 후 온보딩을 완료할 수 있습니다.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND",
                        "회원 정보를 찾을 수 없습니다."));
        member.completeOnboarding();
    }
}
