package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 적재 정합 verify(3c-1, brewery 마스터). 골든 brewery raw 속성 + 채번원장을 조인해 brewery 59행을 적재한 뒤:
 *   (1) 59행·BRW-001~059 연속·유니크
 *   (2) 상호명 non-null
 *   (3) visit 3-state 분포(예약 UNKNOWN 31 / 상시 UNKNOWN 9 — 골든 null율과 일치)
 *   (4) 계산 파생 초기값(전 행 join_status=UNJOINED · liquor_status=NA · sido/region null)
 *   (5) 속성 무손실(전 행 주소·홈페이지·조회수·visit == 골든 원문, NFC 상호명 매칭 기준)
 *   (6) 멱등(재적재 시 신규 0·스킵 59)
 * ★조인·커버리지·주종 카운트는 검증하지 않는다(3c-2). 적재만.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — brewery 적재 verify 스킵")
class BreweryMasterLoadConsistencyTest {

    @Autowired
    private BreweryMasterLoadService loadService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private BreweryMasterLoadService.LoadResult firstResult;

    @BeforeEach
    void loadBreweryMaster() {
        // FK 자식(product_brewery_link·override)이 brewery를 참조하므로 자식 먼저 비운 뒤 brewery 비우고 재적재
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        firstResult = loadService.load(goldenBreweryAttributeRows());
    }

    @Test
    @DisplayName("적재 결과: 원장 59행 전부 적재(신규 59·스킵 0)")
    void loadsAll59Rows() {
        assertThat(firstResult.ledgerRows()).isEqualTo(59);
        assertThat(firstResult.loaded()).isEqualTo(59);
        assertThat(firstResult.skippedExisting()).isZero();
        assertThat(breweryRepository.count()).isEqualTo(59);
    }

    @Test
    @DisplayName("채번: BRW-001~059 연속·유니크·누락 없음")
    void breweryIdsAreContiguous() {
        List<String> ids = breweryRepository.findAll().stream()
                .map(Brewery::getBreweryId).sorted().toList();
        assertThat(ids).hasSize(59);
        for (int i = 1; i <= 59; i++) {
            assertThat(ids.get(i - 1)).isEqualTo(String.format("BRW-%03d", i));
        }
    }

    @Test
    @DisplayName("상호명: 전 행 non-null")
    void businessNamesNonNull() {
        assertThat(breweryRepository.findAll())
                .allSatisfy(b -> assertThat(b.getBusinessName()).isNotBlank());
    }

    @Test
    @DisplayName("visit 3-state 분포: 예약 UNKNOWN 31·Y 24·N 4 / 상시 UNKNOWN 9·Y 35·N 15 (골든 null율 일치)")
    void visitStateDistributionMatchesGoldenNullRates() {
        List<Brewery> all = breweryRepository.findAll();
        assertThat(all.stream().filter(b -> b.getReservationVisitState() == VisitState.UNKNOWN).count()).isEqualTo(31);
        assertThat(all.stream().filter(b -> b.getReservationVisitState() == VisitState.Y).count()).isEqualTo(24);
        assertThat(all.stream().filter(b -> b.getReservationVisitState() == VisitState.N).count()).isEqualTo(4);
        assertThat(all.stream().filter(b -> b.getAlwaysVisitState() == VisitState.UNKNOWN).count()).isEqualTo(9);
        assertThat(all.stream().filter(b -> b.getAlwaysVisitState() == VisitState.Y).count()).isEqualTo(35);
        assertThat(all.stream().filter(b -> b.getAlwaysVisitState() == VisitState.N).count()).isEqualTo(15);
    }

    @Test
    @DisplayName("계산 파생 초기값: 전 행 join_status=UNJOINED·liquor_status=NA·sido/region null (★이번 미계산)")
    void derivedColumnsHaveInitialValues() {
        assertThat(breweryRepository.findAll()).allSatisfy(b -> {
            assertThat(b.getJoinStatus()).isEqualTo(JoinStatus.UNJOINED);
            assertThat(b.getLiquorStatus()).isEqualTo(LiquorStatus.NA);
            assertThat(b.getSido()).isNull();
            assertThat(b.getRegion()).isNull();
        });
    }

    @Test
    @DisplayName("속성 무손실: 전 행 주소·홈페이지·조회수·visit == 골든 원문(NFC 상호명 매칭)")
    void attributesAreLossless() throws IOException {
        Map<String, JsonNode> goldenByNfcName = goldenRowsByNfcName();
        for (Brewery b : breweryRepository.findAll()) {
            JsonNode row = goldenByNfcName.get(nfc(b.getBusinessName()));
            assertThat(row).as("골든 매칭 행 존재: %s", b.getBusinessName()).isNotNull();
            assertThat(b.getAddress()).isEqualTo(text(row, "주소"));
            assertThat(b.getHomepageUrl()).isEqualTo(text(row, "홈페이지"));
            assertThat(b.getViewCount()).isEqualTo(row.get("조회수").longValue());
            assertThat(b.getReservationVisitState()).isEqualTo(VisitState.fromRaw(text(row, "예약방문가능여부")));
            assertThat(b.getAlwaysVisitState()).isEqualTo(VisitState.fromRaw(text(row, "상시방문가능여부")));
            assertThat(b.getNorm()).isNotBlank();
        }
    }

    @Test
    @DisplayName("멱등: 재적재 시 신규 0·스킵 59, 행수 불변")
    void reloadIsIdempotent() {
        BreweryMasterLoadService.LoadResult again = loadService.load(goldenBreweryAttributeRows());
        assertThat(again.loaded()).isZero();
        assertThat(again.skippedExisting()).isEqualTo(59);
        assertThat(breweryRepository.count()).isEqualTo(59);
    }

    // ── 골든 픽스처 로딩 헬퍼 ─────────────────────────────────────────────────
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

    private Map<String, JsonNode> goldenRowsByNfcName() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                FixtureRawSnapshotSource.fixturePath(RawDataset.BREWERY))) {
            assertThat(in).isNotNull();
            JsonNode data = objectMapper.readTree(in).get("data");
            Map<String, JsonNode> map = new HashMap<>();
            data.forEach(row -> map.put(nfc(row.get("상호명").asText()), row));
            return map;
        }
    }

    private static String nfc(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    /** JSON null → Java null 보존. */
    private static String text(JsonNode row, String key) {
        JsonNode v = row.get(key);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
