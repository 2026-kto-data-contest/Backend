package com.jeontongjuro.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.search.recent.RecentSearchRepository;
import com.jeontongjuro.backend.security.session.AuthCookieManager;
import com.jeontongjuro.backend.security.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요)")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfTokenReuseApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RecentSearchRepository recentSearchRepository;
    @Autowired
    private SessionService sessionService;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        recentSearchRepository.deleteAll();
        Member member = memberRepository.findByKakaoUserId(900000003L)
                .orElseGet(() -> memberRepository.save(
                        Member.createKakao(900000003L, "CSRF 재사용 테스트", "csrf@example.com")));
        sessionCookie = new Cookie(AuthCookieManager.SESSION_COOKIE, sessionService.create(member));
        sessionCookie.setPath("/");
    }

    @Test
    void csrfTokenCanBeReusedForConsecutivePostRequests() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrfResponse = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String headerName = csrfResponse.get("headerName").asText();
        String token = csrfResponse.get("token").asText();
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", unmask(token));
        csrfCookie.setPath("/");

        mockMvc.perform(post("/api/v1/search/recent")
                        .cookie(sessionCookie, csrfCookie)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recentSearchBody("BRW-001", "갈기산")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(post("/api/v1/search/recent")
                        .cookie(sessionCookie, csrfCookie)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recentSearchBody("BRW-002", "고도리 와이너리")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private String recentSearchBody(String id, String displayName) {
        return """
                {"type":"BREWERY","id":"%s","keyword":"%s","displayName":"%s"}
                """.formatted(id, displayName, displayName);
    }

    private String unmask(String maskedToken) {
        byte[] maskedBytes = Base64.getUrlDecoder().decode(maskedToken);
        int tokenLength = maskedBytes.length / 2;
        byte[] tokenBytes = new byte[tokenLength];
        for (int index = 0; index < tokenLength; index++) {
            tokenBytes[index] = (byte) (maskedBytes[index] ^ maskedBytes[index + tokenLength]);
        }
        return new String(tokenBytes, StandardCharsets.UTF_8);
    }
}
