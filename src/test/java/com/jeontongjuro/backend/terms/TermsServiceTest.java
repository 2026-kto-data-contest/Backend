package com.jeontongjuro.backend.terms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.terms.dto.TermsAgreementRequest;
import com.jeontongjuro.backend.terms.dto.OptionalTermsAgreementRequest;
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

    @Test
    void allowsMarketingAgreementToBeWithdrawnSeparately() {
        TermsDefinitionRepository definitionRepository = mock(TermsDefinitionRepository.class);
        TermsAgreementRepository agreementRepository = mock(TermsAgreementRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        TermsService service = new TermsService(definitionRepository, agreementRepository, memberRepository);
        Member member = mock(Member.class);
        TermsDefinition marketing = mock(TermsDefinition.class);
        TermsAgreement agreement = mock(TermsAgreement.class);
        when(marketing.getId()).thenReturn(new TermsDefinitionId("MARKETING", "1.0"));
        when(marketing.isRequired()).thenReturn(false);
        when(marketing.getTitle()).thenReturn("마케팅 정보 수신 동의");
        when(definitionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(marketing));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
        when(agreementRepository.findByMemberIdAndTermCodeAndTermVersion(10L, "MARKETING", "1.0"))
                .thenReturn(Optional.of(agreement));

        var response = service.updateOptionalAgreement(
                10L, "MARKETING", new OptionalTermsAgreementRequest(false));

        assertThat(response.code()).isEqualTo("MARKETING");
        assertThat(response.agreed()).isFalse();
        verify(agreement).update(false);
    }

    @Test
    void requiredAgreementCannotBeWithdrawnFromSettingsApi() {
        TermsDefinitionRepository definitionRepository = mock(TermsDefinitionRepository.class);
        TermsAgreementRepository agreementRepository = mock(TermsAgreementRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        TermsService service = new TermsService(definitionRepository, agreementRepository, memberRepository);
        TermsDefinition required = mock(TermsDefinition.class);
        when(required.getId()).thenReturn(new TermsDefinitionId("SERVICE_USE", "1.0"));
        when(required.isRequired()).thenReturn(true);
        when(definitionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(required));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(mock(Member.class)));

        assertThatThrownBy(() -> service.updateOptionalAgreement(
                10L, "SERVICE_USE", new OptionalTermsAgreementRequest(false)))
                .isInstanceOf(AuthException.class)
                .hasMessage("필수 약관은 이 API에서 철회할 수 없습니다.");
    }
}
