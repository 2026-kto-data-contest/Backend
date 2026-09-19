package com.jeontongjuro.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    void allowsAbsoluteReturnToOnlyForConfiguredFrontendOrigin() {
        AppProperties properties = new AppProperties(
                "https://jeontongjuro.com",
                List.of("https://jeontongjuro.com", "http://localhost:5173"));

        assertThat(properties.safeReturnTo("http://localhost:5173/breweries?region=충청"))
                .isEqualTo("http://localhost:5173/breweries?region=충청");
        assertThat(properties.frontendUrl("http://localhost:5173/breweries"))
                .isEqualTo("http://localhost:5173/breweries");
    }

    @Test
    void rejectsUnconfiguredAbsoluteReturnTo() {
        AppProperties properties = new AppProperties(
                "https://jeontongjuro.com",
                List.of("https://jeontongjuro.com"));

        assertThat(properties.safeReturnTo("https://evil.example/phishing")).isEqualTo("/");
        assertThat(properties.safeReturnTo("//evil.example/phishing")).isEqualTo("/");
    }
}
