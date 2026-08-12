package com.jeontongjuro.backend.brewery.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRegionUpdateService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.feature.BreweryFeatureTag;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.JoinSource;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/**
 * 양조장 상세 조회 API 인수 검증(GET /api/v1/breweries/{breweryId}). 리스트 인수 테스트와 동일 패턴:
 * 골든 59행을 적재해 웹 스택 전체(컨트롤러·서비스·배치 조회·직렬화·404 어드바이스)를 통과시키고,
 * 도수·주종·대표 이미지 파생값은 <b>전용 소형 픽스처</b>로 계약만 검증한다(라이브 골든 수치 억지 대입 금지).
 * <p>
 * 검증 축:
 *   (1) 존재하는 breweryId → 200 + 리스트에 없는 상세 필드(address·좌표·homepageUrl) 노출
 *   (2) 없는 breweryId → 404 + 에러 바디 {code=BREWERY_NOT_FOUND, message}
 *   (3) 도수: min의 최솟값·max의 최댓값(GROUP BY), 다중 링크 dedup, 도수 미상 → null
 *   (4) 주종: distinct 배열(선언 순서), 없으면 빈 배열
 *   (5) 특징 태그: 선언 순서 배열, 없으면 빈 배열
 *   (6) 대표 이미지: Type1→modifiable=true / Type3→false, content_id 없거나 first_image 공백 → mainImage=null
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 상세 API verify 스킵")
class BreweryDetailApiTest {

    // 골든 실재 양조장(리스트 테스트와 동일 선정): A=국순당(강원) B=고도리(경상) C=금정산성(부산) D=BRW-001
    private static final String BREWERY_A = "BRW-004";
    private static final String BREWERY_B = "BRW-002";
    private static final String BREWERY_C = "BRW-006";
    private static final String BREWERY_D = "BRW-001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BreweryMasterLoadService loadService;
    @Autowired
    private BreweryRegionUpdateService regionUpdateService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private TourContentRepository tourContentRepository;

    @BeforeEach
    void seedBreweryWithRegion() {
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());
        regionUpdateService.apply();
    }

    @Test
    @DisplayName("존재하는 양조장 → 200 + 상세 필드(주소·좌표·홈페이지) 노출, 기본 계약")
    void existingBreweryReturnsDetail() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breweryId").value(BREWERY_D))
                .andExpect(jsonPath("$.businessName").isNotEmpty())
                .andExpect(jsonPath("$.address").isNotEmpty())
                // 파생값 기본형: 미보유는 빈 배열/null (좌표·홈페이지 키 존재는 별도 테스트에서 has()로 검증 —
                // 이 픽스처는 지오코딩을 돌리지 않아 값이 null일 수 있어 exists()로는 검증하지 않는다)
                .andExpect(jsonPath("$.featureTags").isArray())
                .andExpect(jsonPath("$.liquorTypes").isArray())
                .andExpect(jsonPath("$.mainImage").doesNotExist());
    }

    @Test
    @DisplayName("리스트에 없는 상세 필드: 주소·좌표·홈페이지 키가 실제로 존재한다")
    void detailExposesFieldsAbsentFromList() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_D));
        assertThat(body.has("address")).isTrue();
        assertThat(body.has("latitude")).isTrue();
        assertThat(body.has("longitude")).isTrue();
        assertThat(body.has("homepageUrl")).isTrue();
    }

    @Test
    @DisplayName("없는 breweryId → 404 + 에러 바디 {code=BREWERY_NOT_FOUND, message}")
    void unknownBreweryReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}", "BRW-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BREWERY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── 도수 집계(판단1: DB MIN/MAX GROUP BY, null 무시 규약) ──────────────────────
    @Test
    @DisplayName("도수 단일값: A(6.0~6.0) → alcoholMin=6.0, alcoholMax=6.0")
    void abvSingleValue() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcoholMin").value(6.0))
                .andExpect(jsonPath("$.alcoholMax").value(6.0));
    }

    @Test
    @DisplayName("도수 범위(min≠max): B(25.0~54.0) → alcoholMin=25.0, alcoholMax=54.0")
    void abvMinNotEqualMax() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcoholMin").value(25.0))
                .andExpect(jsonPath("$.alcoholMax").value(54.0));
    }

    @Test
    @DisplayName("다중 링크 집계: C(40.0 링크 2개) → alcoholMin=40.0, alcoholMax=40.0(GROUP BY 단일행)")
    void abvMultipleLinksAggregated() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcoholMin").value(40.0))
                .andExpect(jsonPath("$.alcoholMax").value(40.0));
    }

    @Test
    @DisplayName("도수 미상(null 링크만): D → alcoholMin=null, alcoholMax=null")
    void abvNullWhenNoAbvLink() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcoholMin").doesNotExist())
                .andExpect(jsonPath("$.alcoholMax").doesNotExist());
    }

    // ── 주종 노출(distinct, 선언 순서) ────────────────────────────────────────────
    @Test
    @DisplayName("주종 배열: A에 4종 태깅 → [탁주,약주,청주,증류주] 선언 순서")
    void liquorTypesDistinctSorted() throws Exception {
        seedLiquorTags();
        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_A));
        List<String> types = new ArrayList<>();
        body.get("liquorTypes").forEach(n -> types.add(n.asText()));
        assertThat(types).containsExactly("탁주", "약주", "청주", "증류주");
    }

    @Test
    @DisplayName("주종 미보유 → 빈 배열")
    void liquorTypesEmptyWhenNone() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquorTypes").isArray())
                .andExpect(jsonPath("$.liquorTypes.length()").value(0));
    }

    // ── 특징 태그(선언 순서, 빈 배열) ─────────────────────────────────────────────
    @Test
    @DisplayName("특징 태그: 선언 역순으로 심어도 응답은 선언 순서(수상이력→유기농)")
    void featureTagsSortedByDeclaration() throws Exception {
        featureTagRepository.save(BreweryFeatureTag.of(BREWERY_A, FeatureType.유기농, 8001, "유기농"));
        featureTagRepository.save(BreweryFeatureTag.of(BREWERY_A, FeatureType.수상이력, 8002, null));

        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_A));
        List<String> tags = new ArrayList<>();
        body.get("featureTags").forEach(n -> tags.add(n.asText()));
        assertThat(tags).containsExactly("수상이력", "유기농");
    }

    // ── 대표 이미지(판단2 + 수정1: modifiable 파생) ──────────────────────────────
    @Test
    @DisplayName("대표 이미지 Type1 → mainImage{url,copyright=Type1,modifiable=true}")
    void mainImageType1Modifiable() throws Exception {
        attachImage(BREWERY_A, "CONTENT-A", "http://img/type1.jpg", "Type1");
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImage.url").value("http://img/type1.jpg"))
                .andExpect(jsonPath("$.mainImage.copyright").value("Type1"))
                .andExpect(jsonPath("$.mainImage.modifiable").value(true));
    }

    @Test
    @DisplayName("대표 이미지 Type3 → modifiable=false(변경금지)")
    void mainImageType3NotModifiable() throws Exception {
        attachImage(BREWERY_B, "CONTENT-B", "http://img/type3.jpg", "Type3");
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImage.copyright").value("Type3"))
                .andExpect(jsonPath("$.mainImage.modifiable").value(false));
    }

    @Test
    @DisplayName("content_id는 있으나 first_image 공백 → mainImage=null(빈 URL 미노출)")
    void mainImageNullWhenBlankImage() throws Exception {
        attachImage(BREWERY_C, "CONTENT-C", "   ", "Type1");
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImage").doesNotExist());
    }

    @Test
    @DisplayName("content_id 미매칭 → mainImage=null")
    void mainImageNullWhenNoContent() throws Exception {
        mockMvc.perform(get("/api/v1/breweries/{id}", BREWERY_D))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImage").doesNotExist());
    }

    // ── 상세 필드 편입(#50, additive) ────────────────────────────────────────────
    @Test
    @DisplayName("상세 신규 필드 키 존재: overview·phone·operatingHours·foundedYear 등 11키")
    void detailExposesNewDetailFieldKeys() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_D));
        assertThat(body.has("overview")).isTrue();
        assertThat(body.has("phone")).isTrue();
        assertThat(body.has("phoneSource")).isTrue();
        assertThat(body.has("operatingHours")).isTrue();
        assertThat(body.has("restDate")).isTrue();
        assertThat(body.has("parkingInfo")).isTrue();
        assertThat(body.has("accomCount")).isTrue();
        assertThat(body.has("foundedYear")).isTrue();
        assertThat(body.has("representativeName")).isTrue();
        assertThat(body.has("designatedYear")).isTrue();
        assertThat(body.has("designationNote")).isTrue();
    }

    @Test
    @DisplayName("상세 신규 필드 값: 설정하면 그대로 노출(운영시간·전화·설립연도·소개글)")
    void detailReturnsNewFieldValuesWhenSet() throws Exception {
        Brewery b = breweryRepository.findById(BREWERY_A).orElseThrow();
        b.applyTourDetail("09:00~18:00\n※ 예약 필수", "매주 월요일", "가능", "최대 80명");
        b.applyPhone("041-363-9063", com.jeontongjuro.backend.brewery.PhoneSource.TOUR);
        b.applyNonglim(1933, "김동교", 2013, "80년 전통 3대째 양조장 운영");
        breweryRepository.save(b);
        attachOverview(BREWERY_A, "CONTENT-OV", "1918년 창업한 유서깊은 양조장 소개글");

        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_A));
        assertThat(body.get("phone").asText()).isEqualTo("041-363-9063");
        assertThat(body.get("phoneSource").asText()).isEqualTo("TOUR");
        assertThat(body.get("operatingHours").asText()).contains("09:00~18:00");
        assertThat(body.get("restDate").asText()).isEqualTo("매주 월요일");
        assertThat(body.get("parkingInfo").asText()).isEqualTo("가능");
        assertThat(body.get("accomCount").asText()).isEqualTo("최대 80명");
        assertThat(body.get("foundedYear").asInt()).isEqualTo(1933);
        assertThat(body.get("representativeName").asText()).isEqualTo("김동교");
        assertThat(body.get("designatedYear").asInt()).isEqualTo(2013);
        assertThat(body.get("designationNote").asText()).contains("80년 전통");
        assertThat(body.get("overview").asText()).contains("1918년");
    }

    @Test
    @DisplayName("소개글 미백필(content_id 미매칭) → overview=null")
    void overviewNullWhenNoContent() throws Exception {
        JsonNode body = readBody(get("/api/v1/breweries/{id}", BREWERY_D));
        assertThat(body.get("overview").isNull()).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    /** tour_content에 overview를 백필하고 brewery.content_id를 매칭한다(상세 소개글 경로 검증용). */
    private void attachOverview(String breweryId, String contentId, String overview) {
        TourContentRow row = new TourContentRow(
                contentId, "12", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        TourContent tc = TourContent.create(row, null, null);
        tc.backfillOverview(overview, OffsetDateTime.now(ZoneOffset.UTC));
        tourContentRepository.save(tc);
        Brewery b = breweryRepository.findById(breweryId).orElseThrow();
        b.applyContentMatch(contentId, OffsetDateTime.now(ZoneOffset.UTC));
        breweryRepository.save(b);
    }

    /** 도수 픽스처(리스트 테스트와 동일 값). C는 링크 2개(집계 dedup), D는 null(도수 미상). */
    private void seedAbvLinks() {
        linkRepository.save(ProductBreweryLink.of(9101, "도수-A", "국순당", "국순당", BREWERY_A, JoinSource.AUTO,
                new BigDecimal("6.0"), new BigDecimal("6.0")));
        linkRepository.save(ProductBreweryLink.of(9102, "도수-B", "고도리", "고도리", BREWERY_B, JoinSource.AUTO,
                new BigDecimal("25.0"), new BigDecimal("54.0")));
        linkRepository.save(ProductBreweryLink.of(9103, "도수-C", "금정산성", "금정산성", BREWERY_C, JoinSource.AUTO,
                new BigDecimal("40.0"), new BigDecimal("40.0")));
        linkRepository.save(ProductBreweryLink.of(9106, "도수-C2", "금정산성", "금정산성", BREWERY_C, JoinSource.AUTO,
                new BigDecimal("40.0"), new BigDecimal("40.0")));
        linkRepository.save(ProductBreweryLink.of(9104, "도수-D", "미상", "미상", BREWERY_D, JoinSource.AUTO,
                null, null));
    }

    /** 주종 픽스처. A(9001)에 4개 주종 태그 — uq는 (source_row_ref, liquor_type)이라 정상. */
    private void seedLiquorTags() {
        linkRepository.save(ProductBreweryLink.of(9001, "제품-A", "국순당", "국순당", BREWERY_A, JoinSource.AUTO, null, null));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.탁주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.약주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.청주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.증류주));
    }

    /**
     * 특정 양조장에 대표 이미지를 붙인다. tour_content를 먼저 저장(FK 안전)하고 brewery.content_id를 매칭한다.
     * first_image·cpyrht_div_cd만 채우고 나머지 TourContentRow 필드는 계약과 무관해 null로 둔다.
     */
    private void attachImage(String breweryId, String contentId, String firstImage, String cpyrhtDivCd) {
        TourContentRow row = new TourContentRow(
                contentId, "39", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                firstImage, null, cpyrhtDivCd, null, null, null);
        tourContentRepository.save(TourContent.create(row, null, null));

        Brewery b = breweryRepository.findById(breweryId).orElseThrow();
        b.applyContentMatch(contentId, OffsetDateTime.now(ZoneOffset.UTC));
        breweryRepository.save(b);
    }

    private JsonNode readBody(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
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
