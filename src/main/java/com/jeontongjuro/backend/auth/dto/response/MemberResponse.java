package com.jeontongjuro.backend.auth.dto.response;

import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
        @Schema(description = "서비스 회원 ID", example = "1") Long id,
        @Schema(description = "카카오 프로필 닉네임", example = "전통주러버") String nickname,
        @Schema(description = "카카오 이메일. 제공 동의를 받지 못하면 null", nullable = true,
                example = "user@example.com") String email,
        @Schema(description = "회원 권한", example = "USER") MemberRole role,
        @Schema(description = "현재 버전의 필수 약관 동의 완료 여부") boolean termsAgreed,
        @Schema(description = "온보딩 완료 여부") boolean onboardingCompleted
) {

    public static MemberResponse from(Member member, boolean termsAgreed) {
        return new MemberResponse(member.getId(), member.getNickname(), member.getEmail(), member.getRole(),
                termsAgreed, member.isOnboardingCompleted());
    }
}
