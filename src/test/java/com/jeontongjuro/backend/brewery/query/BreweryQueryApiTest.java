package com.jeontongjuro.backend.brewery.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRegionUpdateService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorType;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.JoinSource;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 양조장 리스트 조회 API 인수 검증(이슈 #13). 골든 brewery 59행을 적재하고 sido/region을 채운 뒤
 * GET /api/v1/breweries를 MockMvc로 호출해 웹 스택 전체(컨트롤러·검증·Specification·직렬화·에러 어드바이스)를 본다:
 *   (1) 필터 없음 → totalElements 59
 *   (2) region 8칩 골든 개수(수도권 13·경상 14·전라 8)
 *   (3) visit 분포 골든(예약 Y 24·상시 UNKNOWN 9)
 *   (4) 잘못된 값 400 + 에러 바디 {code,message}(region=경기·reservationVisit=X)
 *   (5) size 상한 클램프(1000→100)
 *   (6) 고정 정렬(business_name ASC, brewery_id ASC) 전순서 — 59건 중복·누락 없이 오름차순
 *   (7) keyword 부분일치 1건 이상 포함
 * ★수치는 골든/DB 실측 대조값이다(재계산 금지). DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 조회 API verify 스킵")
class BreweryQueryApiTest {

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
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;

    @BeforeEach
    void seedBreweryWithRegion() {
        // FK 자식(link·override) 먼저 비운 뒤 brewery 재적재 → sido/region 채움(파생 UPDATE)
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
    @DisplayName("필터 없음: totalElements == 59 (전체 골든)")
    void noFilterReturnsAll59() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(59))
                .andExpect(jsonPath("$.content.length()").value(59));
    }

    @Test
    @DisplayName("region 칩 골든: 수도권 13 · 경상 14 · 전라 8")
    void regionChipGoldenCounts() throws Exception {
        assertRegionCount("수도권", 13);
        assertRegionCount("경상", 14);
        assertRegionCount("전라", 8);
    }

    @Test
    @DisplayName("visit 분포 골든: 예약방문 Y 24 · 상시방문 UNKNOWN 9")
    void visitStateGoldenCounts() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("reservationVisit", "Y").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(24));
        mockMvc.perform(get("/api/v1/breweries").param("alwaysVisit", "UNKNOWN").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    @Test
    @DisplayName("잘못된 region=경기(정의 밖 시도) → 400 + 에러 바디 {code,message}")
    void invalidRegionYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("region", "경기"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("잘못된 reservationVisit=X(enum 밖) → 400 + 에러 바디 {code,message}")
    void invalidReservationVisitYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("reservationVisit", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("size 상한 클램프: size=1000 요청 → 응답 size == 100")
    void sizeIsClampedTo100() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(59))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("고정 정렬 전순서: business_name ASC · brewery_id ASC — 59건 오름차순, 중복·누락 없음")
    void fixedSortIsTotalOrderOver59() throws Exception {
        JsonNode content = readContent(get("/api/v1/breweries").param("size", "100"));
        assertThat(content).hasSize(59);

        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        content.forEach(item -> {
            names.add(item.get("businessName").asText());
            ids.add(item.get("breweryId").asText());
        });

        // (1) business_name 비내림차순, 동명 구간은 brewery_id 오름차순(전순서 확정)
        for (int i = 1; i < content.size(); i++) {
            int nameCmp = names.get(i - 1).compareTo(names.get(i));
            assertThat(nameCmp).as("business_name 오름차순 위반: %s > %s", names.get(i - 1), names.get(i))
                    .isLessThanOrEqualTo(0);
            if (nameCmp == 0) {
                assertThat(ids.get(i - 1).compareTo(ids.get(i)))
                        .as("동명 시 brewery_id 오름차순 위반: %s > %s", ids.get(i - 1), ids.get(i))
                        .isLessThan(0);
            }
        }
        // (2) 누락·중복 없음: 59개 distinct brewery_id
        assertThat(ids).doesNotHaveDuplicates().hasSize(59);
        // (3) 가장 앞은 가나다 최소(갈기산) — 콜레이션 정상 확인
        assertThat(names.get(0)).isEqualTo("갈기산");
    }

    @Test
    @DisplayName("keyword 부분일치: '안동소주' → 1건 이상, 결과 전부 해당 문자열 포함")
    void keywordPartialMatch() throws Exception {
        JsonNode content = readContent(
                get("/api/v1/breweries").param("keyword", "안동소주").param("size", "100"));
        assertThat(content).isNotEmpty();
        content.forEach(item ->
                assertThat(item.get("businessName").asText()).contains("안동소주"));
    }

    // ── 주종 필터(이슈 #24) ────────────────────────────────────────────────────
    // 골든 59행 중 실재 3곳에 최소 픽스처를 심는다(시드/추론 파이프라인 미의존 — "주어진 데이터에 대한 필터 정확성"만 검증):
    //   A=BRW-004(강원, 국순당)  : 탁주·약주·청주·증류주 4행 — 1:N 중복 유발원
    //   B=BRW-002(경상, 고도리)  : 탁주 1행
    //   C=BRW-006(부산, 금정산성): 증류주 1행
    private static final String BREWERY_A = "BRW-004";
    private static final String BREWERY_B = "BRW-002";
    private static final String BREWERY_C = "BRW-006";

    @Test
    @DisplayName("주종 단일 탁주 → A·B 2건")
    void liquorTypeSingleTakju() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "탁주").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("주종 단일 청주 → A 1건")
    void liquorTypeSingleCheongju() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "청주").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("주종 OR 탁주·약주 → A(중복 제거)·B 2건")
    void liquorTypeOrTakjuYakju() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("liquorType", "탁주").param("liquorType", "약주").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("주종 OR 탁주·증류주 → A·B·C 3건")
    void liquorTypeOrTakjuJeungnyuju() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("liquorType", "탁주").param("liquorType", "증류주").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("★주종 4종 전부(탁주·약주·청주·증류주) → EXISTS 중복 제거로 3건 (join이면 6)")
    void liquorTypeAllFourDeduped() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("liquorType", "탁주")
                        .param("liquorType", "약주")
                        .param("liquorType", "청주")
                        .param("liquorType", "증류주")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    @DisplayName("주종 미지정 → 필터 미적용, 전체 59")
    void liquorTypeAbsentReturnsAll() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(59));
    }

    @Test
    @DisplayName("AND 조합: region=강원 & 탁주 → A만(강원) 1건")
    void regionAndLiquorType() throws Exception {
        seedLiquorTags();
        // 탁주 보유는 A(강원)·B(경상). region=강원 AND 탁주 → 강원인 A 하나.
        mockMvc.perform(get("/api/v1/breweries")
                        .param("region", "강원").param("liquorType", "탁주").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].breweryId").value(BREWERY_A));
    }

    @Test
    @DisplayName("페이지네이션: 탁주 필터 size=1 → content 1건이지만 totalElements 2")
    void liquorTypePaginationSizeOne() throws Exception {
        seedLiquorTags();
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "탁주").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("주종 기타 → 400 (enum 값이지만 API 계약 배제)")
    void liquorTypeGitaYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "기타"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("주종 맥주(정의 밖) → 400")
    void liquorTypeUnknownYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "맥주"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    @DisplayName("주종 다중값 중 하나가 기타 → 원소별 검증으로 400")
    void liquorTypeMixedWithGitaYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("liquorType", "탁주").param("liquorType", "기타"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    // ── 도수 필터(이슈 #41) ────────────────────────────────────────────────────
    // ★파이프라인(366 링크)을 적재하지 않는 테스트이므로 라이브 골든(52/30/31)은 재현 불가하다.
    //   전용 ABV 픽스처를 심고 그 픽스처 기준으로 산출한 값을 단정한다(전제4 억지 대입 금지).
    //   판정은 겹침(방식A): product.alcohol_max >= minAbv AND product.alcohol_min <= maxAbv.
    //   A=BRW-004(강원) 6.0~6.0 / B=BRW-002(경상) 25.0~54.0(겹침 핵심) / C=BRW-006(부산) 40.0~40.0(중복 링크로 dedup 검증)
    //   D=BRW-001 null~null(도수 미상 → 어떤 도수 필터에도 안 걸림)
    private static final String BREWERY_D = "BRW-001";

    @Test
    @DisplayName("maxAbv=6 단독(6도 이하 취급) → A만 1건")
    void abvMaxOnly() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries").param("maxAbv", "6").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].breweryId").value(BREWERY_A));
    }

    @Test
    @DisplayName("minAbv=30 단독(30도 이상 취급) → B·C 2건")
    void abvMinOnly() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries").param("minAbv", "30").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("★겹침 판정: minAbv=20&maxAbv=30 → B(25~54)가 포함되어야 한다(완전 포함 구현이면 0건으로 깨짐)")
    void abvOverlapNotContainment() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("minAbv", "20").param("maxAbv", "30").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].breweryId").value(BREWERY_B));
    }

    @Test
    @DisplayName("범위 minAbv=25&maxAbv=54 → B·C 2건")
    void abvRangeBoth() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("minAbv", "25").param("maxAbv", "54").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("도수 미상(null) 링크는 제외: maxAbv=100 → A·B·C 3건(D 없음)")
    void abvNullLinkExcluded() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries").param("maxAbv", "100").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("★EXISTS dedup: C에 매칭 링크 2개여도 minAbv=40 → B·C 2건(join이면 3)")
    void abvExistsDeduped() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries").param("minAbv", "40").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("AND 조합: region=경상 & minAbv=30 → 경상이면서 30도↑ 취급하는 B 1건")
    void abvAndRegion() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries")
                        .param("region", "경상").param("minAbv", "30").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].breweryId").value(BREWERY_B));
    }

    @Test
    @DisplayName("도수 미지정 → 필터 미적용, 전체 59")
    void abvAbsentReturnsAll() throws Exception {
        seedAbvLinks();
        mockMvc.perform(get("/api/v1/breweries").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(59));
    }

    @Test
    @DisplayName("minAbv > maxAbv → 400")
    void abvMinGreaterThanMaxYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("minAbv", "30").param("maxAbv", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("음수 도수(minAbv=-1) → 400")
    void abvNegativeYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("minAbv", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    @DisplayName("100 초과 도수(maxAbv=101) → 400")
    void abvOver100Yields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("maxAbv", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    @DisplayName("파싱 불가 도수(minAbv=abc) → 400(타입 불일치 경로)")
    void abvUnparseableYields400() throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("minAbv", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    /**
     * 도수 필터 검증용 최소 픽스처(주종 픽스처와 독립 — @BeforeEach가 link를 비우므로 각 테스트가 이것만 심는다).
     * C(BRW-006)에 매칭 링크를 2개 심어 EXISTS dedup을 검증하고, D(BRW-001)는 null 도수로 제외를 검증한다.
     */
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

    /**
     * 주종 필터 검증용 최소 픽스처. product_liquor_type.source_row_ref → product_brewery_link.source_row_ref
     * FK를 만족시키려 link도 함께 심는다(@BeforeEach가 link를 비우므로 여기서 세우지 않으면 FK 위반).
     * A는 한 제품(9001)에 4개 주종 태그 — uq는 (source_row_ref, liquor_type)이라 정상이고 1:N 중복을 만든다.
     */
    private void seedLiquorTags() {
        linkRepository.save(ProductBreweryLink.of(9001, "제품-A", "국순당", "국순당", BREWERY_A, JoinSource.AUTO, null, null));
        linkRepository.save(ProductBreweryLink.of(9002, "제품-B", "고도리", "고도리", BREWERY_B, JoinSource.AUTO, null, null));
        linkRepository.save(ProductBreweryLink.of(9003, "제품-C", "금정산성", "금정산성", BREWERY_C, JoinSource.AUTO, null, null));

        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.탁주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.약주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.청주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9001, BREWERY_A, LiquorType.증류주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9002, BREWERY_B, LiquorType.탁주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(9003, BREWERY_C, LiquorType.증류주));
    }

    private void assertRegionCount(String region, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/breweries").param("region", region).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected))
                .andExpect(jsonPath("$.content.length()").value(expected));
    }

    private JsonNode readContent(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("content");
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
