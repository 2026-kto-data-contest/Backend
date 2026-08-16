package com.jeontongjuro.backend.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_user_id", nullable = false, unique = true)
    private Long kakaoUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String nickname;

    @Column(columnDefinition = "text")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private MemberRole role;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "post_login_return_to", columnDefinition = "text")
    private String postLoginReturnTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Member createKakao(Long kakaoUserId, String nickname, String email) {
        Member member = new Member();
        member.kakaoUserId = kakaoUserId;
        member.nickname = nickname;
        member.email = email;
        member.role = MemberRole.USER;
        member.onboardingCompleted = false;
        return member;
    }

    public void updateKakaoProfile(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    public void rememberPostLoginReturnTo(String returnTo) {
        this.postLoginReturnTo = returnTo;
    }

    public String consumePostLoginReturnTo() {
        String returnTo = postLoginReturnTo;
        postLoginReturnTo = null;
        return returnTo;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
