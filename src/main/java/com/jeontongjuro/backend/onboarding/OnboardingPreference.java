package com.jeontongjuro.backend.onboarding;

import com.jeontongjuro.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "member_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private PreferenceCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static OnboardingPreference create(Member member, PreferenceCategory category, String value) {
        OnboardingPreference preference = new OnboardingPreference();
        preference.member = member;
        preference.category = category;
        preference.value = value;
        preference.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        return preference;
    }
}
