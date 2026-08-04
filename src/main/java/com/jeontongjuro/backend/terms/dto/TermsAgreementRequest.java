package com.jeontongjuro.backend.terms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TermsAgreementRequest(
        @Schema(description = "화면에 표시된 약관별 동의 결과 목록") @NotNull @Valid List<Choice> agreements
) {

    public record Choice(
            @Schema(description = "약관 코드: SERVICE_USE, PRIVACY, LOCATION, MARKETING 중 하나",
                    example = "SERVICE_USE") @NotBlank String code,
            @Schema(description = "사용자가 동의했으면 true, 동의하지 않았으면 false", example = "true")
            @NotNull Boolean agreed
    ) {
    }
}
