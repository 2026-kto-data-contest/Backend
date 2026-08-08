package com.jeontongjuro.backend.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void normalizesAndDeduplicatesOrigins() {
        CorsProperties properties = new CorsProperties(List.of(
                " http://localhost:5173 ",
                "https://frontend.example.com",
                "http://localhost:5173"));

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5173",
                "https://frontend.example.com");
    }

    @Test
    void rejectsOriginContainingPath() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://frontend.example.com/path")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("경로 없는 origin");
    }
}
