package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.JoinSource;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.math.BigDecimal;
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
 * 제품 목록 조회 API 인수 검증(GET /api/v1/breweries/{breweryId}/products). 라이브 골든 수치에 의존하지 않고,
 * BRW-001에 합성 제품을 심어 파이프라인 계약(판매중단·원본오류 제외 → 병합 → 주종 억제 → 정렬 → 페이징)을
 * 결정론으로 검증한다. 실데이터 회귀는 라이브 실측(수동)과 {@link ProductQueryGoldenTest}가 담당한다.
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 제품 목록 API verify 스킵")
class ProductQueryApiTest {

    private static final String BREWERY = "BRW-001";
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 28);
    private static final Instant COLLECTED_AT = Instant.parse("2026-07-28T00:00:00Z");

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
        // 제품 계열만 청소한다(내 합성 데이터 격리). brewery는 FK 자식(brewery_nearby 등)이 있어 지우지 않고,
        // 멱등 로드로 BRW-001 존재만 보장한다(기존 행은 existsById로 skip). 제품 조회는 BRW-001만 본다.
        liquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        productRawRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());

        // 수상 제품(정렬 선두) — 완결 설명, 대상
        product(90001, "수상제품", "온전히 끝나는 소개 문장입니다.", "2020 대한민국 우리술품평회 대상", "500ml", "Y",
                new BigDecimal("12.0"), new BigDecimal("12.0"));
        liquorTypeRepository.save(ProductLiquorType.manual(90001, BREWERY, LiquorType.탁주));

        // 무수상 제품
        product(90002, "무수상제품", "짧은 소개 문장이다.", null, "750ml", "Y", null, null);
        liquorTypeRepository.save(ProductLiquorType.manual(90002, BREWERY, LiquorType.청주));

        // 중복 병합 쌍 — 90010(설명 미완결 null) vs 90011(완결). 대표는 완결설명 보유한 90011(작은 ref 아님)
        product(90010, "중복 술", noSentenceEnd78(), null, "300ml", "Y", new BigDecimal("9.0"), new BigDecimal("9.0"));
        product(90011, "중복술", "완결된 소개 문장이다.", null, "300ml", "Y", new BigDecimal("9.0"), new BigDecimal("9.0"));

        // 판매중단 → 제외
        product(90020, "판매중단제품", "소개.", null, "500ml", "N", null, null);

        // 원본오류 시드(1142) → 제외
        product(1142, "청명주오류", "소개.", null, "375ml", "Y", null, null);

        // 유일 주종이 suppressed_from_tab=true라도 제품 카드엔 노출된다(억제는 주종 탭 전용 플래그).
        product(90030, "억제주종제품", "소개.", null, "500ml", "Y", null, null);
        liquorTypeRepository.save(ProductLiquorType.auto(90030, BREWERY, LiquorType.탁주, true, "억제kw"));

        // 3주종 전부 노출(억제 여부 무관) — 억제된 증류주도 카드엔 나온다.
        product(90040, "이주종제품", "소개.", null, "500ml", "Y", null, null);
        liquorTypeRepository.save(ProductLiquorType.manual(90040, BREWERY, LiquorType.탁주));
        liquorTypeRepository.save(ProductLiquorType.manual(90040, BREWERY, LiquorType.청주));
        liquorTypeRepository.save(ProductLiquorType.auto(90040, BREWERY, LiquorType.증류주, true, "억제kw"));
    }

    @Test
    @DisplayName("totalElements=5(판매중단·원본오류 제외 + 중복 병합 후), 수상 제품이 정렬 선두")
    void totalAndSortAndExclusions() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "10"));
        assertThat(body.get("totalElements").asInt()).isEqualTo(5);
        assertThat(body.get("content")).hasSize(5);
        // 정렬: 수상 보유 우선
        assertThat(body.get("content").get(0).get("productId").asInt()).isEqualTo(90001);
        assertThat(body.get("content").get(0).get("awardBadge").asText()).isEqualTo("대상");

        List<Integer> ids = new ArrayList<>();
        body.get("content").forEach(c -> ids.add(c.get("productId").asInt()));
        assertThat(ids).doesNotContain(90020); // 판매중단
        assertThat(ids).doesNotContain(1142);  // 원본오류 시드
    }

    @Test
    @DisplayName("중복 병합: '중복 술'/'중복술' → 카드 1건, 대표=완결설명 보유 90011, 설명은 완결본")
    void duplicateMergeUsesCompletableDescriptionRepresentative() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "10"));
        List<JsonNode> merged = new ArrayList<>();
        body.get("content").forEach(c -> {
            if (c.get("productName").asText().replaceAll("\\s", "").equals("중복술")) {
                merged.add(c);
            }
        });
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).get("productId").asInt()).isEqualTo(90011);
        assertThat(merged.get(0).get("productName").asText()).isEqualTo("중복술");
        assertThat(merged.get(0).get("description").asText()).isEqualTo("완결된 소개 문장이다.");
    }

    @Test
    @DisplayName("주종 억제 미적용: 유일 주종이 suppressed여도 노출(90030) · 억제 증류주 포함 전종 노출(90040)")
    void suppressedFlagNotAppliedToProductCards() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "10"));

        // 유일 주종이 억제(suppressed_from_tab=true)인 제품 — 이제 그 주종이 카드에 노출된다(실데이터 9건과 동형).
        JsonNode uniqueSuppressed = cardById(body, 90030);
        List<String> uniqueTypes = new ArrayList<>();
        uniqueSuppressed.get("liquorTypes").forEach(n -> uniqueTypes.add(n.asText()));
        assertThat(uniqueTypes).containsExactly("탁주");

        // 억제된 증류주도 제외되지 않고 나머지와 함께 노출(주종 enum ordinal 순서: 탁주·청주·증류주).
        JsonNode multi = cardById(body, 90040);
        List<String> multiTypes = new ArrayList<>();
        multi.get("liquorTypes").forEach(n -> multiTypes.add(n.asText()));
        assertThat(multiTypes).containsExactly("탁주", "청주", "증류주");
    }

    @Test
    @DisplayName("페이지네이션: size=2 → totalPages 3, 각 페이지 결정론적 슬라이스")
    void pagination() throws Exception {
        JsonNode p0 = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "2").param("page", "0"));
        assertThat(p0.get("totalElements").asInt()).isEqualTo(5);
        assertThat(p0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(p0.get("content")).hasSize(2);
        assertThat(p0.get("content").get(0).get("productId").asInt()).isEqualTo(90001);

        JsonNode p2 = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "2").param("page", "2"));
        assertThat(p2.get("content")).hasSize(1);
    }

    @Test
    @DisplayName("클램프: page=-1 → 0, size=0 → 5, size=200 → 100 (400 아님)")
    void clamps() throws Exception {
        JsonNode negPage = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("page", "-1"));
        assertThat(negPage.get("page").asInt()).isEqualTo(0);

        JsonNode zeroSize = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "0"));
        assertThat(zeroSize.get("size").asInt()).isEqualTo(5);

        JsonNode bigSize = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "200"));
        assertThat(bigSize.get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("없는 breweryId → 404 {code=BREWERY_NOT_FOUND, message}")
    void unknownBrewery404() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}/products", "BRW-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BREWERY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("page 타입 파싱 실패 → 400 INVALID_QUERY_PARAMETER")
    void typeMismatch400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    @DisplayName("도수 단일값·용량 원문·주종 배열 계약")
    void basicCardContract() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}/products", BREWERY).param("size", "10"));
        JsonNode card = cardById(body, 90001);
        assertThat(card.get("alcoholMin").asDouble()).isEqualTo(12.0);
        assertThat(card.get("alcoholMax").asDouble()).isEqualTo(12.0);
        assertThat(card.get("volume").asText()).isEqualTo("500ml");
        List<String> types = new ArrayList<>();
        card.get("liquorTypes").forEach(n -> types.add(n.asText()));
        assertThat(types).containsExactly("탁주");
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private JsonNode cardById(JsonNode body, int productId) {
        for (JsonNode c : body.get("content")) {
            if (c.get("productId").asInt() == productId) {
                return c;
            }
        }
        throw new AssertionError("카드 없음: " + productId);
    }

    /** 마침표가 없는(문장 끝 없는) 정확히 78자 소개 — 되돌림 결과가 null(미노출)이 되는 미완결 케이스. */
    private static String noSentenceEnd78() {
        StringBuilder sb = new StringBuilder("쉼표만 이어지는 미완결 소개, 문장이 끝나지 않고, 계속, 흘러가는, 문구");
        while (sb.length() < 78) {
            sb.append('가');
        }
        return sb.substring(0, 78);
    }

    private void product(int ref, String name, String description, String awards, String volume, String saleYn,
                         BigDecimal alcoholMin, BigDecimal alcoholMax) {
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
                alcoholMin, alcoholMax));
    }

    private JsonNode readBody(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
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
