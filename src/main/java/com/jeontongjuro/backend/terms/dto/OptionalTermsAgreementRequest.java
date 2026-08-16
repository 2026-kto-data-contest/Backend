package com.jeontongjuro.backend.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OptionalTermsAgreementRequest(
        @Schema(description = "변경할 선택 약관 동의 여부", example = "false")
        @NotNull Boolean agreed
) {
}
