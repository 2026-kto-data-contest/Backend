package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 양조장 목록 조회의 맛 태그 배치 지원({@link ProductQueryService#representativeCharacteristicsByBreweryId})
 * 검증. buildCards(제품 목록 API)와 동일한 대표 선정·정렬 규칙(수상 보유 우선 → productId 오름차순)을
 * 배치로 재현하는지, 여러 양조장을 한 번에 묶어도 서로 섞이지 않는지, 제품 없는 양조장은 결과에 없는지를 본다.
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 배치 재사용 verify 스킵")
class ProductQueryServiceCharacteristicsTest {

    private static final String BREWERY_A = "BRW-001";
    private static final String BREWERY_B = "BRW-002";
    private static final String BREWERY_C = "BRW-003"; // 제품 없음
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 28);
    private static final Instant COLLECTED_AT = Instant.parse("2026-07-28T00:00:00Z");

    @Autowired
    private ProductQueryService productQueryService;
    @Autowired
    private BreweryMasterLoadService loadService;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ProductLiquorTypeRepository liquorTypeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seed() {
        liquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        productRawRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());

        // A: 무수상(낮은 ref, "여기는 절대 선택되면 안 됨") vs 수상(높은 ref) — 그룹별 정렬은 수상 보유 우선이라
        //    ref가 더 커도 수상 있는 쪽이 대표가 돼야 한다(buildCards ⑧ 정렬과 동치 검증).
        product(BREWERY_A, 95001, "무수상제품", "여기는 절대 선택되면 안 됨", null, "Y");
        product(BREWERY_A, 95002, "수상제품", "부드러운 목넘김과 산뜻한 산미", "2020 우리술품평회 대상", "Y");

        // B: 단일 제품(그룹 1개) — 그대로 대표.
        product(BREWERY_B, 95010, "단일제품", "달콤하고 향긋한 뒷맛", null, "Y");

        // C: 제품 없음(seed 안 함) — 결과 Map에 없어야 한다.
    }

    @Test
    @DisplayName("수상 보유 그룹이 낮은 productId 그룹을 이기고 대표가 된다(buildCards ⑧ 정렬과 동치)")
    void awardGroupWinsOverLowerProductId() {
        Map<String, String> result = productQueryService
                .representativeCharacteristicsByBreweryId(List.of(BREWERY_A));
        assertThat(result.get(BREWERY_A)).isEqualTo("부드러운 목넘김과 산뜻한 산미");
    }

    @Test
    @DisplayName("여러 양조장을 한 번에 배치해도 서로 섞이지 않는다")
    void batchAcrossMultipleBreweriesDoesNotCrossContaminate() {
        Map<String, String> result = productQueryService
                .representativeCharacteristicsByBreweryId(List.of(BREWERY_A, BREWERY_B));
        assertThat(result.get(BREWERY_A)).isEqualTo("부드러운 목넘김과 산뜻한 산미");
        assertThat(result.get(BREWERY_B)).isEqualTo("달콤하고 향긋한 뒷맛");
    }

    @Test
    @DisplayName("제품이 없는 양조장은 결과 Map에 없다(호출자가 null 취급)")
    void breweryWithNoProductsIsAbsent() {
        Map<String, String> result = productQueryService
                .representativeCharacteristicsByBreweryId(List.of(BREWERY_A, BREWERY_C));
        assertThat(result).containsKey(BREWERY_A);
        assertThat(result).doesNotContainKey(BREWERY_C);
    }

    @Test
    @DisplayName("빈 breweryId 목록 → 빈 Map(쿼리 미발생)")
    void emptyInputYieldsEmptyMap() {
        assertThat(productQueryService.representativeCharacteristicsByBreweryId(List.of())).isEmpty();
    }

    @Test
    @DisplayName("추천 코스 페어링 원문에 노출 제품의 특징을 포함한다")
    void pairingTextsIncludeCharacteristics() {
        assertThat(productQueryService.pairingTexts(BREWERY_A))
                .contains("여기는 절대 선택되면 안 됨", "부드러운 목넘김과 산뜻한 산미");
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private void product(String breweryId, int ref, String name, String characteristics, String awards,
                         String saleYn) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("제품명", name);
        node.put("특징", characteristics);
        if (awards != null) {
            node.put("수상경력", awards);
        }
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
