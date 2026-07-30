package com.jeontongjuro.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryJoinStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.JoinStatus;
import com.jeontongjuro.backend.brewery.LiquorStatus;
import com.jeontongjuro.backend.override.ManualOverride;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.override.OverrideType;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.pipeline.normalize.BusinessNameNormalizer;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 조인 커버리지 verify(3c-2). brewery 59 + override 9 선적재 후 product 1215 전체를 조인해:
 *   (1) AUTO 커버리지 56 (골든 56/59와 대조 — 회귀 감지)
 *   (2) MANUAL 개선 2(양촌와이너리·조옥화)로 합계 58 확정
 *   (3) UNJOINED는 정확히 밀과노닐다(BRW-018) 단독
 *   (4) liquor_status 2축 정합(JOINED→UNTAGGED, UNJOINED→NA)
 *   (5) override 9행 각각 실제 연결 실측(ROW_PIN 2행 조옥화 포함 — 골든 원문 교정 후 재발 방지 가드)
 *   (6) AUTO↔override 충돌 0건
 *   (7) product_norm ↔ 현재 normalizer 재계산 일치(코드 드리프트 감시)
 *   (8) 멱등
 * ★주소 파싱·주종 롤업(TAGGED 판정)은 검증하지 않는다(3d).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 조인 커버리지 verify 스킵")
class ProductBreweryJoinConsistencyTest {

    private static final String MILGWA_NONILDA_ID = "BRW-018";
    private static final Set<String> MANUAL_IMPROVED_BREWERIES = Set.of("BRW-033", "BRW-003");

    @Autowired
    private BreweryMasterLoadService breweryLoadService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideSeedLoadService overrideLoadService;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryJoinService joinService;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private BreweryJoinStatusUpdateService statusUpdateService;
    @Autowired
    private ObjectMapper objectMapper;

    private ProductBreweryJoinService.JoinResult joinResult;

    @BeforeEach
    void loadBreweryOverrideThenJoin() {
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        breweryLoadService.load(goldenBreweryAttributeRows());
        overrideLoadService.load();
        joinResult = joinService.join(goldenProductRows());
    }

    @Test
    @DisplayName("AUTO 커버리지: distinct brewery 56 (골든 56/59와 일치 — 회귀 감지)")
    void autoLinkedBreweryCountIs56() {
        Set<String> autoBreweries = new HashSet<>();
        for (ProductBreweryLink link : linkRepository.findByJoinSource(JoinSource.AUTO)) {
            autoBreweries.add(link.getBreweryId());
        }
        assertThat(autoBreweries).hasSize(56);
    }

    @Test
    @DisplayName("MANUAL 개선: override로 새로 연결되는 brewery == {양촌와이너리(BRW-033), 조옥화(BRW-003)}")
    void overrideAddsExactlyTwoBreweriesBeyondAuto() {
        Set<String> autoBreweries = new HashSet<>();
        for (ProductBreweryLink link : linkRepository.findByJoinSource(JoinSource.AUTO)) {
            autoBreweries.add(link.getBreweryId());
        }
        Set<String> manualOnlyBreweries = new HashSet<>();
        for (ProductBreweryLink link : linkRepository.findAll()) {
            if (link.getJoinSource() != JoinSource.AUTO && !autoBreweries.contains(link.getBreweryId())) {
                manualOnlyBreweries.add(link.getBreweryId());
            }
        }
        assertThat(manualOnlyBreweries).isEqualTo(MANUAL_IMPROVED_BREWERIES);
    }

    @Test
    @DisplayName("합계 커버리지: AUTO∪MANUAL distinct brewery == 58/59")
    void totalLinkedBreweryCoverageIs58() {
        assertThat(joinService.linkedBreweryIds()).hasSize(58);
    }

    @Test
    @DisplayName("AUTO↔override 충돌 0건(같은 product가 서로 다른 brewery로 이중 매칭되는 숨은 오병합 신호 없음)")
    void noAutoOverrideConflicts() {
        assertThat(joinResult.autoOverrideConflicts()).isZero();
    }

    @Test
    @DisplayName("override 9행 실측: 전 행이 실제로 1개 이상 product를 연결(0건 침묵 금지)")
    void everyOverrideActuallyLinksAtLeastOneProduct() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all).hasSize(9);
        for (ManualOverride o : all) {
            int hits = joinResult.overrideHitCounts().getOrDefault(o.getId(), 0);
            assertThat(hits).as("override id=%d (%s, match_key=%s) 실제 연결 건수", o.getId(), o.getOverrideType(), o.getMatchKey())
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("ROW_PIN 재발 방지 가드: 조옥화 2행(match_key=골든 원문 교정본)이 정확히 2건 BRW-003에 연결")
    void rowPinOverridesLinkExactlyTwoJoOkHwaProducts() {
        assertThat(joinResult.overrideRowLinked()).isEqualTo(2);
        List<ManualOverride> rowPins = overrideRepository.findAll().stream()
                .filter(o -> o.getOverrideType() == OverrideType.ROW_PIN)
                .toList();
        assertThat(rowPins).hasSize(2);
        for (ManualOverride rowPin : rowPins) {
            int hits = joinResult.overrideHitCounts().getOrDefault(rowPin.getId(), 0);
            assertThat(hits).as("ROW_PIN match_key=%s 실제 연결 건수(골든 원문 완전일치 필요)", rowPin.getMatchKey())
                    .isEqualTo(1);
            assertThat(rowPin.getBreweryId()).isEqualTo("BRW-003");
        }
        List<ProductBreweryLink> rowPinLinks = linkRepository.findByJoinSource(JoinSource.OVERRIDE_ROW);
        assertThat(rowPinLinks).hasSize(2);
        assertThat(rowPinLinks).allSatisfy(link -> assertThat(link.getBreweryId()).isEqualTo("BRW-003"));
    }

    @Test
    @DisplayName("brewery UPDATE 후: JOINED 58 · UNJOINED 정확히 밀과노닐다(BRW-018) 단독")
    void breweryStatusUpdateYields58JoinedAndSoleUnjoined() {
        statusUpdateService.applyJoinResult(joinService.linkedBreweryIds());

        List<Brewery> all = breweryRepository.findAll();
        List<Brewery> joined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.JOINED).toList();
        List<Brewery> unjoined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.UNJOINED).toList();

        assertThat(joined).hasSize(58);
        assertThat(unjoined).hasSize(1);
        assertThat(unjoined.get(0).getBreweryId()).isEqualTo(MILGWA_NONILDA_ID);
        assertThat(unjoined.get(0).getBusinessName()).isEqualTo("밀과노닐다");
    }

    @Test
    @DisplayName("liquor_status 2축 정합: JOINED 58 전부 UNTAGGED, UNJOINED 1 NA (JOINED인데 NA 금지)")
    void liquorStatusTwoAxisConsistency() {
        statusUpdateService.applyJoinResult(joinService.linkedBreweryIds());

        for (Brewery b : breweryRepository.findAll()) {
            if (b.getJoinStatus() == JoinStatus.JOINED) {
                assertThat(b.getLiquorStatus()).as("JOINED인데 NA 금지: %s", b.getBreweryId())
                        .isEqualTo(LiquorStatus.UNTAGGED);
            } else {
                assertThat(b.getLiquorStatus()).as("UNJOINED는 NA 유지: %s", b.getBreweryId())
                        .isEqualTo(LiquorStatus.NA);
            }
        }
    }

    @Test
    @DisplayName("product_norm ↔ 현재 BusinessNameNormalizer 재계산 일치(코드 드리프트 감시)")
    void productNormMatchesCurrentNormalizerRecalculation() {
        for (ProductBreweryLink link : linkRepository.findAll()) {
            assertThat(link.getProductNorm())
                    .isEqualTo(BusinessNameNormalizer.normalize(link.getBreweryNameRaw()));
        }
    }

    @Test
    @DisplayName("멱등: 재조인 시 신규 0·스킵 = 기존 연결 행수")
    void reJoinIsIdempotent() {
        long before = linkRepository.count();
        ProductBreweryJoinService.JoinResult again = joinService.join(goldenProductRows());
        assertThat(again.linked()).isZero();
        assertThat(again.skippedExisting()).isEqualTo(before);
        assertThat(linkRepository.count()).isEqualTo(before);
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

    /** 골든 product raw 1215건을 sourceRowIndex(전역 0-based) 부여해 List<ProductRaw>로 변환. */
    private List<ProductRaw> goldenProductRows() {
        RawSnapshot snapshot = new FixtureRawSnapshotSource(objectMapper).fetch(RawDataset.PRODUCT);
        java.time.LocalDate snapshotDate = java.time.LocalDate.ofInstant(snapshot.collectedAt(), ZoneOffset.UTC);
        List<ProductRaw> rows = new ArrayList<>(snapshot.rows().size());
        for (int i = 0; i < snapshot.rows().size(); i++) {
            try {
                ProductRaw row = objectMapper.treeToValue(snapshot.rows().get(i), ProductRaw.class);
                row.assignLoadKey(snapshotDate, i, snapshot.collectedAt());
                rows.add(row);
            } catch (IOException e) {
                throw new IllegalStateException("골든 product raw 역직렬화 실패(index=" + i + ")", e);
            }
        }
        return rows;
    }
}
