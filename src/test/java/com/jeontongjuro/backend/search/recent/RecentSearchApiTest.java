package com.jeontongjuro.backend.search.recent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import java.util.stream.IntStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요)")
class RecentSearchApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecentSearchRepository recentSearchRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Member member;
    private Member otherMember;

    @BeforeEach
    void setUp() {
        recentSearchRepository.deleteAll();
        member = memberRepository.findByKakaoUserId(900000001L)
                .orElseGet(() -> memberRepository.save(
                        Member.createKakao(900000001L, "최근검색 테스트", "recent@example.com")));
        otherMember = memberRepository.findByKakaoUserId(900000002L)
                .orElseGet(() -> memberRepository.save(
                        Member.createKakao(900000002L, "다른 회원", "other@example.com")));
    }

    @Test
    void saveListRefreshAndDeleteFlow() throws Exception {
        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BREWERY"))
                .andExpect(jsonPath("$.id").value("BRW-001"))
                .andExpect(jsonPath("$.keyword").value("갈기산"));

        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산 양조장")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword").value("갈기산 양조장"));

        mockMvc.perform(get("/api/v1/search/recent").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].keyword").value("갈기산 양조장"))
                .andExpect(jsonPath("$[0].searchedAt").value(org.hamcrest.Matchers.endsWith("Z")));

        Long recentSearchId = recentSearchRepository.findAll().get(0).getId();
        mockMvc.perform(delete("/api/v1/search/recent/{id}", recentSearchId)
                        .with(auth(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/search/recent").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyLatestTenAreRetained() throws Exception {
        IntStream.rangeClosed(1, 11).forEach(index -> {
            try {
                mockMvc.perform(post("/api/v1/search/recent")
                                .with(auth(member)).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"type":"BREWERY","id":"BRW-%03d","keyword":"검색어%d","displayName":"양조장%d"}
                                        """.formatted(index, index, index)))
                        .andExpect(status().isOk());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        mockMvc.perform(get("/api/v1/search/recent").param("limit", "10").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("BRW-011"));
    }

    @Test
    void memberCannotDeleteAnotherMembersEntry() throws Exception {
        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산")))
                .andExpect(status().isOk());
        Long recentSearchId = recentSearchRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/v1/search/recent/{id}", recentSearchId)
                        .with(auth(otherMember)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECENT_SEARCH_NOT_FOUND"));
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mockMvc.perform(get("/api/v1/search/recent"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestAndLimitReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"BREWERY","id":"PRD-0003","keyword":"갈기산","displayName":"갈기산"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECENT_SEARCH"));

        mockMvc.perform(get("/api/v1/search/recent").param("limit", "11").with(auth(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECENT_SEARCH"));
    }

    @Test
    void deleteAllOnlyDeletesCurrentMembersEntries() throws Exception {
        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/search/recent")
                        .with(auth(otherMember)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(breweryBody("갈기산")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/search/recent").with(auth(member)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/search/recent").with(auth(member)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/search/recent").with(auth(otherMember)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private RequestPostProcessor auth(Member target) {
        AuthenticatedMember principal = new AuthenticatedMember(
                target.getId(), target.getEmail(), target.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + target.getRole().name()))));
    }

    private String breweryBody(String keyword) {
        return """
                {"type":"BREWERY","id":"BRW-001","keyword":"%s","displayName":"갈기산"}
                """.formatted(keyword);
    }
}
