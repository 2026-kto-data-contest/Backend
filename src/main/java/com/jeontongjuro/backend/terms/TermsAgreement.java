package com.jeontongjuro.backend.terms;

import com.jeontongjuro.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "term_code", nullable = false, columnDefinition = "text")
    private String termCode;

    @Column(name = "term_version", nullable = false, columnDefinition = "text")
    private String termVersion;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public static TermsAgreement record(Member member, TermsDefinition definition, boolean agreed) {
        TermsAgreement agreement = new TermsAgreement();
        agreement.member = member;
        agreement.termCode = definition.getId().code();
        agreement.termVersion = definition.getId().version();
        agreement.update(agreed);
        return agreement;
    }

    public void update(boolean agreed) {
        this.agreed = agreed;
        this.recordedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
