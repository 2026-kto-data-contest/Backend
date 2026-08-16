package com.jeontongjuro.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.terms.TermsService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OnboardingServiceTest {

    @Test
    void completesOnboardingAfterRequiredTermsAgreement() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        TermsService termsService = mock(TermsService.class);
        OnboardingService service = new OnboardingService(memberRepository, termsService);
        Member member = mock(Member.class);
        when(termsService.hasRequiredAgreements(10L)).thenReturn(true);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        service.complete(10L);

        verify(member).completeOnboarding();
    }

    @Test
    void rejectsOnboardingBeforeRequiredTermsAgreement() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        TermsService termsService = mock(TermsService.class);
        OnboardingService service = new OnboardingService(memberRepository, termsService);
        when(termsService.hasRequiredAgreements(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(10L))
                .isInstanceOf(AuthException.class)
                .hasMessage("필수 약관 동의 후 온보딩을 완료할 수 있습니다.");
    }
}
