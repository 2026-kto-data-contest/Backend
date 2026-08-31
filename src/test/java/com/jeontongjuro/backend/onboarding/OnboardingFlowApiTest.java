package com.jeontongjuro.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.security.session.AuthCookieManager;
import com.jeontongjuro.backend.security.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 실제 PostgreSQL·세션 필터·CSRF 필터를 모두 통과하는 온보딩 회귀 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ONBOARDING_AUDIT_ENABLED", matches = "true",
        disabledReason = "실제 DB 통합 검증은 ONBOARDING_AUDIT_ENABLED=true일 때만 실행")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OnboardingFlowApiTest {

    private static final long KAKAO_USER_ID = 900000110L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private OnboardingPreferenceRepository preferenceRepository;
    @Autowired private SessionService sessionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Member member;
    private Cookie sessionCookie;
    private Cookie csrfCookie;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        deleteTestMember();
        member = memberRepository.saveAndFlush(
                Member.createKakao(KAKAO_USER_ID, "온보딩 통합 테스트", "onboarding110@example.com"));
        sessionCookie = cookie(AuthCookieManager.SESSION_COOKIE, sessionService.create(member));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessionCookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        csrfHeader = csrf.get("headerName").asText();
        csrfToken = csrf.get("token").asText();
        csrfCookie = cookie(AuthCookieManager.CSRF_COOKIE, unmask(csrfToken));
    }

    @AfterEach
    void tearDown() {
        deleteTestMember();
    }

    @Test
    void preferencesArePersistedAndReturnedThroughAuthenticatedApi() throws Exception {
        String body = """
                {"liquorTypes":["탁주","약주"],"regions":["수도권"],"alcoholLevel":"MEDIUM"}
                """;

        mockMvc.perform(put("/api/v1/onboarding/preferences")
                        .cookie(sessionCookie, csrfCookie).header(csrfHeader, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquorTypes.length()").value(2))
                .andExpect(jsonPath("$.alcoholLevel").value("MEDIUM"));

        assertThat(preferenceRepository.findByMemberId(member.getId()))
                .extracting(OnboardingPreference::getValue)
                .containsExactlyInAnyOrder("탁주", "약주", "수도권", "MEDIUM");

        mockMvc.perform(get("/api/v1/onboarding/preferences").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquorTypes[0]").value("약주"))
                .andExpect(jsonPath("$.liquorTypes[1]").value("탁주"))
                .andExpect(jsonPath("$.regions[0]").value("수도권"))
                .andExpect(jsonPath("$.alcoholLevel").value("MEDIUM"));
    }

    @Test
    void preferenceWriteWithoutCsrfReturnsClientVisible403() throws Exception {
        mockMvc.perform(put("/api/v1/onboarding/preferences")
                        .cookie(sessionCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liquorTypes\":[\"탁주\"],\"regions\":[],\"alcoholLevel\":\"LIGHT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void completionWithoutPreferencesReturns409AfterRequiredTermsAgreement() throws Exception {
        agreeToTerms();

        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .cookie(sessionCookie, csrfCookie).header(csrfHeader, csrfToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_PREFERENCES_REQUIRED"));
    }

    @Test
    void continueAfterTermsUsesStateBasedNextPath() throws Exception {
        agreeToTerms();

        mockMvc.perform(post("/api/v1/auth/continue")
                        .cookie(sessionCookie, csrfCookie).header(csrfHeader, csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPath").value("/onboarding"));
    }

    @Test
    void memberTimestampRoundTripsAsUtcInstantAndConvertsToKst() {
        String dataType = jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = current_schema() and table_name = 'member_account' and column_name = 'created_at'
                """, String.class);
        assertThat(dataType).isEqualTo("timestamp with time zone");

        Member loaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toInstant())
                .isEqualTo(loaded.getCreatedAt());
        assertThat(loaded.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).getOffset().getTotalSeconds())
                .isEqualTo(9 * 60 * 60);
    }

    private void agreeToTerms() throws Exception {
        String body = """
                {"agreements":[
                  {"code":"SERVICE_USE","agreed":true},
                  {"code":"PRIVACY","agreed":true},
                  {"code":"LOCATION","agreed":false},
                  {"code":"MARKETING","agreed":false}
                ]}
                """;
        mockMvc.perform(post("/api/v1/terms/agreements")
                        .cookie(sessionCookie, csrfCookie).header(csrfHeader, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Cookie cookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        return cookie;
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

    private void deleteTestMember() {
        memberRepository.findByKakaoUserId(KAKAO_USER_ID).ifPresent(memberRepository::delete);
        memberRepository.flush();
    }
}
