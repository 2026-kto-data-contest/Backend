package com.jeontongjuro.backend.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요)")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void serviceApiContainsFrontendEndpointsAndHidesOAuthCallback() throws Exception {
        mockMvc.perform(get("/v3/api-docs/service-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/csrf']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/terms']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/terms/agreements']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/terms/agreements/{code}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/continue']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/complete']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/preferences'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/preferences'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/breweries']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/callback']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JT_SESSION"));
    }
}
