package com.jeontongjuro.backend.terms;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    List<TermsAgreement> findByMemberId(Long memberId);

    Optional<TermsAgreement> findByMemberIdAndTermCodeAndTermVersion(
            Long memberId, String termCode, String termVersion);
}
