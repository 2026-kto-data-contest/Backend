package com.jeontongjuro.backend.onboarding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OnboardingPreferencesRequest(
        @Schema(description = "선호 주종", example = "[\"탁주\", \"약주\"]")
        @NotEmpty List<String> liquorTypes,
        @Schema(description = "선호 지역. 빈 배열이면 전국을 의미", example = "[\"수도권\", \"강원\"]")
        List<String> regions,
        @Schema(description = "선호 도수 구간: LIGHT(7도 미만), MEDIUM(7~20도 미만), STRONG(20도 이상)",
                example = "MEDIUM")
        String alcoholLevel
) {
}
