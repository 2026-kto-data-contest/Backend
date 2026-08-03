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
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
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
 * GET /api/breweries를 MockMvc로 호출해 웹 스택 전체(컨트롤러·검증·Specification·직렬화·에러 어드바이스)를 본다:
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

    @BeforeEach
    void seedBreweryWithRegion() {
        // FK 자식(link·override) 먼저 비운 뒤 brewery 재적재 → sido/region 채움(파생 UPDATE)
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());
        regionUpdateService.apply();
    }

    @Test
    @DisplayName("필터 없음: totalElements == 59 (전체 골든)")
    void noFilterReturnsAll59() throws Exception {
        mockMvc.perform(get("/api/breweries").param("size", "100"))
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
        mockMvc.perform(get("/api/breweries").param("reservationVisit", "Y").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(24));
        mockMvc.perform(get("/api/breweries").param("alwaysVisit", "UNKNOWN").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    @Test
    @DisplayName("잘못된 region=경기(정의 밖 시도) → 400 + 에러 바디 {code,message}")
    void invalidRegionYields400() throws Exception {
        mockMvc.perform(get("/api/breweries").param("region", "경기"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("잘못된 reservationVisit=X(enum 밖) → 400 + 에러 바디 {code,message}")
    void invalidReservationVisitYields400() throws Exception {
        mockMvc.perform(get("/api/breweries").param("reservationVisit", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("size 상한 클램프: size=1000 요청 → 응답 size == 100")
    void sizeIsClampedTo100() throws Exception {
        mockMvc.perform(get("/api/breweries").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(59))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("고정 정렬 전순서: business_name ASC · brewery_id ASC — 59건 오름차순, 중복·누락 없음")
    void fixedSortIsTotalOrderOver59() throws Exception {
        JsonNode content = readContent(get("/api/breweries").param("size", "100"));
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
                get("/api/breweries").param("keyword", "안동소주").param("size", "100"));
        assertThat(content).isNotEmpty();
        content.forEach(item ->
                assertThat(item.get("businessName").asText()).contains("안동소주"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private void assertRegionCount(String region, int expected) throws Exception {
        mockMvc.perform(get("/api/breweries").param("region", region).param("size", "100"))
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
