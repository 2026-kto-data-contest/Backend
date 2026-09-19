package com.jeontongjuro.backend.onboarding.dto;

import java.util.List;

public record OnboardingPreferencesResponse(
        List<String> liquorTypes,
        List<String> regions,
        String alcoholLevel,
        List<String> flavors
) {
    public OnboardingPreferencesResponse(List<String> liquorTypes, List<String> regions, String alcoholLevel) {
        this(liquorTypes, regions, alcoholLevel, List.of());
    }
}
