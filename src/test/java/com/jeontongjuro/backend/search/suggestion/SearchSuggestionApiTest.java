package com.jeontongjuro.backend.search.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.JoinSource;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 검색 자동완성 API 인수 검증(GET /api/v1/search/suggestions). 라이브 골든 수치에 의존하지 않고,
 * BRW-001에 합성 제품을 심어 파이프라인 계약(정렬·트림·길이 제한·판매중단/원본오류 제외·id 형식)을
 * 결정론으로 검증한다({@link com.jeontongjuro.backend.product.query.ProductQueryApiTest}와 동일 패턴).
 * 실데이터 동명 충돌(금풍양조·솔송주·한산소곡주 등) 회귀는 라이브 실측(수동)이 담당한다.
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 검색 자동완성 API verify 스킵")
class SearchSuggestionApiTest {

    private static final String BREWERY = "BRW-001";
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 28);
    private static final Instant COLLECTED_AT = Instant.parse("2026-07-28T00:00:00Z");
    private static final Pattern BREWERY_ID = Pattern.compile("BRW-\\d{3}");
    private static final Pattern PRODUCT_ID = Pattern.compile("PRD-(\\d{4})");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BreweryMasterLoadService loadService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;

    @BeforeEach
    void seed() {
        // 제품 계열만 청소한다(내 합성 데이터 격리). brewery는 FK 자식이 있어 지우지 않고,
        // 멱등 로드로 존재만 보장한다(ProductQueryApiTest와 동일 패턴).
        linkRepository.deleteAll();
        productRawRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());
    }

    @Test
    @DisplayName("매칭 결과 없음: 존재하지 않는 검색어 → 200 + 빈 배열")
    void zeroMatchesReturnEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", "존재하지않는검색어zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("빈 검색 처리: 빈 문자열·공백만 → 트림 후 빈 값이므로 200 + 빈 배열(400 아님)")
    void blankOrWhitespaceKeywordReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/search/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("글자 수 제한: 트림 후 21자 → 400 INVALID_QUERY_PARAMETER")
    void keywordOverTwentyCharsAfterTrimReturns400() throws Exception {
        String twentyOneChars = "가".repeat(21);
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", twentyOneChars))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // 앞뒤 공백은 트림 후 20자로 통과해야 한다(트림 먼저, 그다음 길이 판정).
        String twentyCharsWithPadding = " " + "가".repeat(20) + " ";
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", twentyCharsWithPadding))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("정렬: 전방일치가 부분일치보다 먼저, 동순위(전방일치끼리)는 가나다순")
    void frontMatchSortsBeforePartialMatchThenHangulTiebreak() throws Exception {
        product(210, "SGTApple", "소개.", null, "500ml", "Y");
        product(211, "SGTZebra", "소개.", null, "500ml", "Y");
        product(212, "XSGTMiddle", "소개.", null, "500ml", "Y");

        List<String> names = suggestionDisplayNames("sgt");
        assertThat(names).containsExactly("SGTApple", "SGTZebra", "XSGTMiddle");
    }

    @Test
    @DisplayName("노출 최대 10개: 11건 전방일치 매칭 시 가나다(알파벳)순 상위 10건만 반환")
    void truncatesToTenResultsWhenElevenOrMoreMatch() throws Exception {
        char[] suffixes = "abcdefghijk".toCharArray(); // 11개
        for (int i = 0; i < suffixes.length; i++) {
            product(220 + i, "TRC" + suffixes[i], "소개.", null, "500ml", "Y");
        }

        List<String> names = suggestionDisplayNames("trc");
        assertThat(names).hasSize(10);
        assertThat(names).containsExactly(
                "TRCa", "TRCb", "TRCc", "TRCd", "TRCe", "TRCf", "TRCg", "TRCh", "TRCi", "TRCj");
        assertThat(names).doesNotContain("TRCk");
    }

    @Test
    @DisplayName("판매중단·원본오류 시드 제외 — 노출 모집단(표시집합)과 동일 규칙")
    void discontinuedAndExclusionSeedProductsAreHiddenFromSuggestions() throws Exception {
        // 판매중단(sale_yn=N) — 검색되면 안 됨. 대조군은 노출돼야 함.
        product(240, "DISCa", "소개.", null, "500ml", "N");
        product(241, "DISCcontrol", "소개.", null, "500ml", "Y");

        List<String> discNames = suggestionDisplayNames("disc");
        assertThat(discNames).containsExactly("DISCcontrol");

        // 원본오류 시드(source_row_index=1142, product_exclusion_seed.json) — 검색되면 안 됨.
        product(1142, "EXCLb", "소개.", null, "500ml", "Y");
        product(242, "EXCLcontrol", "소개.", null, "500ml", "Y");

        List<String> exclNames = suggestionDisplayNames("excl");
        assertThat(exclNames).containsExactly("EXCLcontrol");
    }

    @Test
    @DisplayName("id 계약: 응답 id는 RecentSearchService 검증 정규식(BRW-\\d{3} · PRD-\\d{4}, 3~1213)을 항상 만족")
    void suggestionIdsSatisfyRecentSearchValidationContract() throws Exception {
        product(3, "IDCa", "소개.", null, "500ml", "Y");
        product(1213, "IDCb", "소개.", null, "500ml", "Y");

        JsonNode content = readContent(get("/api/v1/search/suggestions").param("keyword", "idc"));
        assertThat(content).isNotEmpty();
        for (JsonNode item : content) {
            assertThat(item.get("type").asText()).isEqualTo("PRODUCT");
            String id = item.get("id").asText();
            Matcher matcher = PRODUCT_ID.matcher(id);
            assertThat(matcher.matches()).as("id '%s'가 PRD-\\d{4} 형식이어야 함", id).isTrue();
            int ref = Integer.parseInt(matcher.group(1));
            assertThat(ref).isBetween(3, 1213);
        }

        JsonNode breweryContent = readContent(get("/api/v1/search/suggestions").param("keyword", "산머루농원"));
        assertThat(breweryContent).isNotEmpty();
        boolean sawBrewery = false;
        for (JsonNode item : breweryContent) {
            if ("BREWERY".equals(item.get("type").asText())) {
                sawBrewery = true;
                assertThat(BREWERY_ID.matcher(item.get("id").asText()).matches()).isTrue();
            }
        }
        assertThat(sawBrewery).isTrue();
    }

    @Test
    @DisplayName("양조장·제품 동명: 병합하지 않고 type이 다른 2행으로 노출(#4)")
    void brewerySameNameAsProductProducesTwoSeparateRows() throws Exception {
        Brewery brw001 = breweryRepository.findById(BREWERY).orElseThrow();
        String collidingName = brw001.getBusinessName(); // 골든: "산머루농원"
        product(230, collidingName, "소개.", null, "500ml", "Y");

        JsonNode content = readContent(get("/api/v1/search/suggestions").param("keyword", collidingName));
        assertThat(content).hasSize(2);

        List<String> types = new ArrayList<>();
        content.forEach(item -> {
            assertThat(item.get("displayName").asText()).isEqualTo(collidingName);
            types.add(item.get("type").asText());
        });
        assertThat(types).containsExactlyInAnyOrder("BREWERY", "PRODUCT");
    }

    @Test
    @DisplayName("특수문자 포함 이름 자기참조: 자동완성이 내려준 이름을 그대로 재검색해도 자기 자신이 잡힌다(#1-B)")
    void selfReferenceMatchesDisplayNameWithSpecialCharacters() throws Exception {
        product(250, "이화주(술샘)", "소개.", null, "500ml", "Y");

        // 표시명 그대로 재검색
        List<String> exact = suggestionDisplayNames("이화주(술샘)");
        assertThat(exact).contains("이화주(술샘)");

        // 특수문자를 뗀 형태로 재검색해도 동일 제품이 잡힌다(정규화 대상 = 매칭용, 표시값은 원문 유지)
        List<String> stripped = suggestionDisplayNames("이화주술샘");
        assertThat(stripped).contains("이화주(술샘)");
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private List<String> suggestionDisplayNames(String keyword) throws Exception {
        JsonNode content = readContent(get("/api/v1/search/suggestions").param("keyword", keyword));
        List<String> names = new ArrayList<>();
        content.forEach(item -> names.add(item.get("displayName").asText()));
        return names;
    }

    private JsonNode readContent(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /** ProductQueryApiTest.product()와 동일 패턴 — product_raw(한글 키) + product_brewery_link를 함께 심는다. */
    private void product(int ref, String name, String description, String awards, String volume, String saleYn) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("제품명", name);
        if (description != null) {
            node.put("제품소개", description);
        }
        if (awards != null) {
            node.put("수상경력", awards);
        }
        if (volume != null) {
            node.put("용량", volume);
        }
        node.put("판매여부", saleYn);
        try {
            ProductRaw raw = objectMapper.treeToValue(node, ProductRaw.class);
            raw.assignLoadKey(SNAPSHOT_DATE, ref, COLLECTED_AT);
            productRawRepository.save(raw);
        } catch (IOException e) {
            throw new IllegalStateException("합성 product_raw 역직렬화 실패", e);
        }
        linkRepository.save(ProductBreweryLink.of(ref, name, "원문양조장", "원문양조장", BREWERY, JoinSource.AUTO,
                null, null));
    }

    private List<BreweryRaw> goldenBreweryAttributeRows() {
        RawSnapshot snapshot = new FixtureRawSnapshotSource(objectMapper).fetch(RawDataset.BREWERY);
        List<BreweryRaw> rows = new ArrayList<>(snapshot.rows().size());
        for (JsonNode node : snapshot.rows()) {
            try {
                rows.add(objectMapper.treeToValue(node, BreweryRaw.class));
            } catch (IOException e) {
                throw new IllegalStateException("골든 brewery raw 역직렬화 실패", e);
            }
        }
        return rows;
    }
}
