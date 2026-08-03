package com.jeontongjuro.backend.terms.dto;

import com.jeontongjuro.backend.terms.TermsDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

public record TermsResponse(
        @Schema(description = "약관 코드", example = "SERVICE_USE") String code,
        @Schema(description = "약관 버전", example = "1.0") String version,
        @Schema(description = "화면에 표시할 약관명", example = "서비스 이용약관 동의") String title,
        @Schema(description = "필수 동의 여부") boolean required,
        @Schema(description = "약관 전문 URL", nullable = true) String contentUrl,
        @Schema(description = "현재 회원의 동의 여부") boolean agreed
) {

    public static TermsResponse from(TermsDefinition definition, boolean agreed) {
        return new TermsResponse(definition.getId().code(), definition.getId().version(), definition.getTitle(),
                definition.isRequired(), definition.getContentUrl(), agreed);
    }
}
