package com.jeontongjuro.backend.onboarding.dto;

import java.util.List;

public record OnboardingPreferencesResponse(
        List<String> liquorTypes,
        List<String> regions,
        String alcoholLevel
) {
}
