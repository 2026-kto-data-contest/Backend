package com.jeontongjuro.backend.onboarding;

import com.jeontongjuro.backend.auth.dto.response.NextPathResponse;
import com.jeontongjuro.backend.auth.service.AuthService;
import com.jeontongjuro.backend.onboarding.dto.OnboardingPreferencesRequest;
import com.jeontongjuro.backend.onboarding.dto.OnboardingPreferencesResponse;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "온보딩", description = "신규 회원 취향 온보딩 완료 API")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final OnboardingPreferenceService preferenceService;
    private final AuthService authService;

    @Operation(summary = "온보딩 취향 조회",
            description = "현재 회원에게 저장된 선호 주종, 지역, 맛 취향을 조회합니다.")
    @GetMapping("/preferences")
    public OnboardingPreferencesResponse getPreferences(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return preferenceService.get(member.id());
    }

    @Operation(summary = "온보딩 취향 저장·수정", description = """
            선호 주종, 지역, 도수 취향을 회원 계정 기준으로 저장합니다.
            기존 선택값은 요청의 선택값으로 교체됩니다. 주종은 한 개 이상, 도수는 하나를 선택해야 하며,
            지역은 빈 배열로 보내면 전국을 선호하는 것으로 처리합니다.
            """)
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @PutMapping("/preferences")
    public OnboardingPreferencesResponse savePreferences(
            @AuthenticationPrincipal AuthenticatedMember member,
            @jakarta.validation.Valid @RequestBody OnboardingPreferencesRequest request) {
        return preferenceService.save(member.id(), request);
    }

    @Operation(
            summary = "온보딩 완료",
            description = """
                    취향 온보딩의 마지막 저장이 성공한 뒤 호출합니다.
                    필수 약관 동의가 완료된 회원만 호출할 수 있습니다.
                    성공하면 온보딩 완료 상태를 저장하고 로그인 직전 화면의 안전한 내부 경로를 반환합니다.
                    """)
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "온보딩 완료 및 다음 경로 반환"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "409", description = "필수 약관 또는 필수 온보딩 취향 저장 필요")
    })
    @PostMapping("/complete")
    public NextPathResponse complete(@AuthenticationPrincipal AuthenticatedMember member) {
        onboardingService.complete(member.id());
        return new NextPathResponse(authService.continueLogin(member.id()));
    }
}
