package com.jeontongjuro.backend.search.unified;

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
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
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
 * 통합 검색 API 인수 검증(GET /api/v1/search). 라이브 골든 수치에 의존하지 않고, 검색어와 겹치지 않는
 * 합성 토큰("ZQX" — 실데이터 상호·제품에 없음)으로 합성 양조장(BRW-9xx)·제품을 심어 계약(순위 서열·순위 내
 * 정렬·중복 제거·totalElements·특수문자 정규화 매칭·판매중단 제외·빈값/길이·페이징)을 결정론으로 검증한다.
 * <p>
 * 합성 양조장은 원장(봉인) 밖 BRW-9xx라 실데이터와 격리된다. 실데이터 tier 서열 육안 확인은 라이브 실측이 담당한다.
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 통합 검색 API verify 스킵")
class UnifiedSearchApiTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 28);
    private static final Instant COLLECTED_AT = Instant.parse("2026-07-28T00:00:00Z");
    /** 합성 양조장 id 풀(원장 밖 — 실데이터 BRW-001~059과 격리). @BeforeEach에서 전부 지운다. */
    private static final List<String> SYNTHETIC_IDS = List.of(
            "BRW-901", "BRW-902", "BRW-903", "BRW-904", "BRW-905",
            "BRW-906", "BRW-907", "BRW-908", "BRW-909", "BRW-910");

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
    @Autowired
    private ProductLiquorTypeRepository liquorTypeRepository;

    @BeforeEach
    void seed() {
        // 제품 계열을 FK 의존 순서로 지우고(주종→링크→raw), 합성 양조장을 제거한 뒤, 실 양조장 59를 멱등 로드한다.
        liquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        productRawRepository.deleteAll();
        breweryRepository.deleteAllById(SYNTHETIC_IDS);
        loadService.load(goldenBreweryAttributeRows());
    }

    @Test
    @DisplayName("순위 서열·순위 내 정렬·중복 제거·totalElements: 1순위(전방)→2순위(부분)→3순위(제품), 순위 내 이름순")
    void tierOrderingInternalSortDedupAndTotal() throws Exception {
        brewery("BRW-901", "ZQXalpha");                 // 1순위(전방)
        brewery("BRW-902", "ZQXbravo");                 // 1순위(전방)
        brewery("BRW-905", "ZQXcharlie");               // 1순위(전방) + 제품도 매칭 → 중복 제거로 1행만
        product("BRW-905", 505, "ZQXjuice", "Y");
        brewery("BRW-903", "preZQXmid");                // 2순위(부분)
        brewery("BRW-904", "plainname");                // 3순위(제품만)
        product("BRW-904", 504, "ZQXsoju", "Y");

        JsonNode body = readBody(get("/api/v1/search").param("keyword", "ZQX"));
        // 1순위 이름순(alpha<bravo<charlie) → 2순위 → 3순위. 905는 이름·제품 양쪽이지만 1순위에 한 번만.
        assertThat(contentIds(body)).containsExactly(
                "BRW-901", "BRW-902", "BRW-905", "BRW-903", "BRW-904");
        assertThat(body.get("totalElements").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("특수문자 정규화 매칭(§4-2): 괄호 포함 이름을 원문 그대로도, 괄호 없이도 재검색하면 매칭된다")
    void specialCharTargetIsNormalizedForMatching() throws Exception {
        brewery("BRW-901", "plainbrewery");
        product("BRW-901", 501, "이화주(술샘)", "Y");   // 자동완성이 이 원문 그대로 내려준다

        // 원문 그대로 재검색 — 입력·대상 모두 정규화(괄호 제거)해 자기 자신에 매칭
        JsonNode asShown = readBody(get("/api/v1/search").param("keyword", "이화주(술샘)"));
        assertThat(contentIds(asShown)).contains("BRW-901");
        assertThat(asShown.get("totalElements").asLong()).isEqualTo(1);

        // 괄호를 뺀 형태로 검색해도 같은 양조장에 매칭
        JsonNode stripped = readBody(get("/api/v1/search").param("keyword", "이화주술샘"));
        assertThat(contentIds(stripped)).contains("BRW-901");
    }

    @Test
    @DisplayName("판매중단·원본오류 제품명은 3순위 매칭에서 제외(표시집합 규칙 동일)")
    void discontinuedAndExclusionProductsDoNotMatchTier3() throws Exception {
        brewery("BRW-901", "plainone");
        product("BRW-901", 501, "ZQXstopped", "N");     // 판매중단 — 검색되면 안 됨
        brewery("BRW-902", "plaintwo");
        product("BRW-902", 502, "ZQXlive", "Y");        // 대조군 — 검색돼야 함
        brewery("BRW-903", "plainthree");
        product("BRW-903", 1142, "ZQXexclseed", "Y");   // 원본오류 시드(1142) — 검색되면 안 됨

        assertThat(readBody(get("/api/v1/search").param("keyword", "ZQXstopped"))
                .get("totalElements").asLong()).isZero();
        assertThat(readBody(get("/api/v1/search").param("keyword", "ZQXexclseed"))
                .get("totalElements").asLong()).isZero();

        JsonNode live = readBody(get("/api/v1/search").param("keyword", "ZQXlive"));
        assertThat(contentIds(live)).containsExactly("BRW-902");
        assertThat(live.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 검색 처리: 빈 문자열·공백만·미전달·특수문자만 → 200 + totalElements 0(400 아님)")
    void blankOrSymbolOnlyKeywordReturnsZeroNot400() throws Exception {
        for (String kw : List.of("", "   ")) {
            JsonNode body = readBody(get("/api/v1/search").param("keyword", kw));
            assertThat(body.get("totalElements").asLong()).isZero();
            assertThat(body.get("content")).isEmpty();
        }
        // 미전달
        assertThat(readBody(get("/api/v1/search")).get("totalElements").asLong()).isZero();
        // 특수문자·이모지만 → 허용문자 제거 후 빈 값 → 0건(에러 아님)
        assertThat(readBody(get("/api/v1/search").param("keyword", "()%,·"))
                .get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("글자 수 제한: 트림 후 21자 → 400, 앞뒤 공백 포함 20자 → 200")
    void keywordOverTwentyCharsAfterTrimReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("keyword", "가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/search").param("keyword", " " + "가".repeat(20) + " "))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("페이징: 경계에서 누락·중복 없이 전체 순서를 이어붙인다, totalPages 정확")
    void pagingHasNoGapNoDuplicateAndCorrectTotalPages() throws Exception {
        brewery("BRW-901", "ZQXa");
        brewery("BRW-902", "ZQXb");
        brewery("BRW-903", "ZQXc");
        brewery("BRW-904", "ZQXd");
        brewery("BRW-905", "ZQXe");

        List<String> collected = new ArrayList<>();
        JsonNode p0 = readBody(get("/api/v1/search").param("keyword", "ZQX").param("page", "0").param("size", "2"));
        JsonNode p1 = readBody(get("/api/v1/search").param("keyword", "ZQX").param("page", "1").param("size", "2"));
        JsonNode p2 = readBody(get("/api/v1/search").param("keyword", "ZQX").param("page", "2").param("size", "2"));
        collected.addAll(contentIds(p0));
        collected.addAll(contentIds(p1));
        collected.addAll(contentIds(p2));

        assertThat(p0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(p0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(contentIds(p0)).hasSize(2);
        assertThat(contentIds(p1)).hasSize(2);
        assertThat(contentIds(p2)).hasSize(1);
        // 이름순 전체 이어붙임 — 누락·중복 없음
        assertThat(collected).containsExactly(
                "BRW-901", "BRW-902", "BRW-903", "BRW-904", "BRW-905");
    }

    @Test
    @DisplayName("page·size 보정: 음수 page→0, size 1미만→기본20, size 100초과→100 (400 아님)")
    void pageAndSizeAreClampedNotRejected() throws Exception {
        brewery("BRW-901", "ZQXsolo");

        JsonNode negPage = readBody(get("/api/v1/search").param("keyword", "ZQX").param("page", "-1"));
        assertThat(negPage.get("page").asInt()).isZero();

        JsonNode zeroSize = readBody(get("/api/v1/search").param("keyword", "ZQX").param("size", "0"));
        assertThat(zeroSize.get("size").asInt()).isEqualTo(20);

        JsonNode hugeSize = readBody(get("/api/v1/search").param("keyword", "ZQX").param("size", "500"));
        assertThat(hugeSize.get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("결과 카드는 양조장 목록 DTO 재사용: breweryId·businessName·featureTags/flavorTags 배열·mainImage null 규약")
    void resultCardReusesBreweryListItemShape() throws Exception {
        brewery("BRW-901", "ZQXcard");

        JsonNode body = readBody(get("/api/v1/search").param("keyword", "ZQX"));
        JsonNode item = body.get("content").get(0);
        assertThat(item.get("breweryId").asText()).isEqualTo("BRW-901");
        assertThat(item.get("businessName").asText()).isEqualTo("ZQXcard");
        assertThat(item.get("featureTags").isArray()).isTrue();
        assertThat(item.get("flavorTags").isArray()).isTrue();
        assertThat(item.get("liquorTypes").isArray()).isTrue();
        // 합성 양조장은 tour_content가 없어 대표 이미지 null(스칼라 결측 규약)
        assertThat(item.get("mainImage").isNull()).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private List<String> contentIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("content").forEach(item -> ids.add(item.get("breweryId").asText()));
        return ids;
    }

    private JsonNode readBody(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private Brewery brewery(String id, String businessName) {
        // address는 NOT NULL 컬럼이라 더미 값을 준다(검색 로직과 무관 — sigungu 파싱 입력일 뿐).
        return breweryRepository.save(Brewery.seed(
                id, businessName, businessName, "테스트특별시 테스트구", null, 0L,
                VisitState.UNKNOWN, VisitState.UNKNOWN));
    }

    /** SearchSuggestionApiTest.product()와 동일 패턴 — product_raw(한글 키) + product_brewery_link를 함께 심는다. */
    private void product(String breweryId, int ref, String name, String saleYn) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("제품명", name);
        node.put("제품소개", "소개.");
        node.put("용량", "500ml");
        node.put("판매여부", saleYn);
        try {
            ProductRaw raw = objectMapper.treeToValue(node, ProductRaw.class);
            raw.assignLoadKey(SNAPSHOT_DATE, ref, COLLECTED_AT);
            productRawRepository.save(raw);
        } catch (IOException e) {
            throw new IllegalStateException("합성 product_raw 역직렬화 실패", e);
        }
        linkRepository.save(ProductBreweryLink.of(ref, name, "원문양조장", "원문양조장", breweryId, JoinSource.AUTO,
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
