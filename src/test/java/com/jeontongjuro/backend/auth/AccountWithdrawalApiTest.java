package com.jeontongjuro.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
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
class AccountWithdrawalApiTest {

    private static final long KAKAO_USER_ID = 900000004L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SessionService sessionService;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        memberRepository.findByKakaoUserId(KAKAO_USER_ID).ifPresent(memberRepository::delete);
        member = memberRepository.save(Member.createKakao(KAKAO_USER_ID, "탈퇴 테스트", "withdraw@example.com"));
        sessionCookie = new Cookie(AuthCookieManager.SESSION_COOKIE, sessionService.create(member));
        sessionCookie.setPath("/");
    }

    @Test
    void authenticatedMemberCanWithdrawAndDependentSessionIsCascadeDeleted() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfResponse = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String headerName = csrfResponse.get("headerName").asText();
        String token = csrfResponse.get("token").asText();
        Cookie csrfCookie = new Cookie(AuthCookieManager.CSRF_COOKIE, unmask(token));
        csrfCookie.setPath("/");

        mockMvc.perform(delete("/api/v1/auth/me")
                        .cookie(sessionCookie, csrfCookie)
                        .header(headerName, token))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.findById(member.getId())).isEmpty();
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
