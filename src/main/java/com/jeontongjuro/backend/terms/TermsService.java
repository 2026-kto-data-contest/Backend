package com.jeontongjuro.backend.terms;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.terms.dto.TermsAgreementRequest;
import com.jeontongjuro.backend.terms.dto.OptionalTermsAgreementRequest;
import com.jeontongjuro.backend.terms.dto.TermsResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermsService {

    private final TermsDefinitionRepository termsDefinitionRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<TermsResponse> getCurrentTerms(Long memberId) {
        Set<String> agreedKeys = termsAgreementRepository.findByMemberId(memberId).stream()
                .filter(TermsAgreement::isAgreed)
                .map(agreement -> key(agreement.getTermCode(), agreement.getTermVersion()))
                .collect(Collectors.toSet());
        return activeTerms().stream()
                .map(term -> TermsResponse.from(term, agreedKeys.contains(key(term))))
                .toList();
    }

    @Transactional
    public List<TermsResponse> agree(Long memberId, TermsAgreementRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."));
        Map<String, Boolean> choices = new HashMap<>();
        for (TermsAgreementRequest.Choice choice : request.agreements()) {
            if (choices.put(choice.code(), choice.agreed()) != null) {
                throw invalidTerms("중복된 약관 코드가 있습니다.");
            }
        }

        List<TermsDefinition> terms = activeTerms();
        Set<String> validCodes = terms.stream().map(term -> term.getId().code()).collect(Collectors.toSet());
        if (!validCodes.containsAll(choices.keySet())) {
            throw invalidTerms("알 수 없는 약관 코드가 있습니다.");
        }
        boolean requiredRejected = terms.stream()
                .filter(TermsDefinition::isRequired)
                .anyMatch(term -> !Boolean.TRUE.equals(choices.get(term.getId().code())));
        if (requiredRejected) {
            throw invalidTerms("필수 약관에 모두 동의해야 합니다.");
        }

        for (TermsDefinition term : terms) {
            boolean agreed = Boolean.TRUE.equals(choices.get(term.getId().code()));
            TermsAgreement agreement = termsAgreementRepository
                    .findByMemberIdAndTermCodeAndTermVersion(memberId, term.getId().code(), term.getId().version())
                    .orElseGet(() -> TermsAgreement.record(member, term, agreed));
            agreement.update(agreed);
            termsAgreementRepository.save(agreement);
        }
        return getCurrentTerms(memberId);
    }

    @Transactional(readOnly = true)
    public boolean hasRequiredAgreements(Long memberId) {
        Map<String, Boolean> choices = termsAgreementRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(
                        agreement -> key(agreement.getTermCode(), agreement.getTermVersion()),
                        TermsAgreement::isAgreed,
                        (first, second) -> second));
        return activeTerms().stream()
                .filter(TermsDefinition::isRequired)
                .allMatch(term -> Boolean.TRUE.equals(choices.get(key(term))));
    }

    @Transactional
    public TermsResponse updateOptionalAgreement(
            Long memberId,
            String code,
            OptionalTermsAgreementRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED, "MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."));
        TermsDefinition term = activeTerms().stream()
                .filter(definition -> definition.getId().code().equals(code))
                .findFirst()
                .orElseThrow(() -> invalidTerms("알 수 없거나 현재 사용하지 않는 약관 코드입니다."));
        if (term.isRequired()) {
            throw invalidTerms("필수 약관은 이 API에서 철회할 수 없습니다.");
        }
        TermsAgreement agreement = termsAgreementRepository
                .findByMemberIdAndTermCodeAndTermVersion(memberId, code, term.getId().version())
                .orElseGet(() -> TermsAgreement.record(member, term, request.agreed()));
        agreement.update(request.agreed());
        termsAgreementRepository.save(agreement);
        return TermsResponse.from(term, request.agreed());
    }

    private List<TermsDefinition> activeTerms() {
        return termsDefinitionRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    private String key(TermsDefinition definition) {
        return key(definition.getId().code(), definition.getId().version());
    }

    private String key(String code, String version) {
        return code + ":" + version;
    }

    private AuthException invalidTerms(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TERMS_AGREEMENT", message);
    }
}
