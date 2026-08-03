package com.jeontongjuro.backend.terms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.terms.dto.TermsAgreementRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TermsServiceTest {

    @Test
    void rejectsWhenRequiredTermIsNotAgreed() {
        TermsDefinitionRepository definitionRepository = mock(TermsDefinitionRepository.class);
        TermsAgreementRepository agreementRepository = mock(TermsAgreementRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        TermsService service = new TermsService(definitionRepository, agreementRepository, memberRepository);
        Member member = mock(Member.class);
        TermsDefinition required = mock(TermsDefinition.class);
        when(required.getId()).thenReturn(new TermsDefinitionId("SERVICE_USE", "1.0"));
        when(required.isRequired()).thenReturn(true);
        when(definitionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(required));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        var request = new TermsAgreementRequest(
                List.of(new TermsAgreementRequest.Choice("SERVICE_USE", false)));

        assertThatThrownBy(() -> service.agree(10L, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("필수 약관에 모두 동의해야 합니다.");
    }
}
