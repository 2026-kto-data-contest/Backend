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
import java.math.BigDecimal;
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
 * 조인 커버리지 verify(3c-2). brewery 59 + override 14 선적재 후 product 1215 전체를 조인해:
 *   (1) AUTO 커버리지 56 (골든 56/59와 대조 — 회귀 감지)
 *   (2) MANUAL 개선 3(양촌와이너리·조옥화·밀과노닐다)로 합계 59 확정
 *   (3) UNJOINED 0건(밀과노닐다 BRW-018 조인 복구)
 *   (4) liquor_status 2축 정합(JOINED→UNTAGGED, UNJOINED→NA)
 *   (5) override 14행 각각 실제 연결 실측(ROW_PIN 조옥화 2·밀과노닐다 4 포함 — 골든 원문 교정 후 재발 방지 가드)
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
    private static final Set<String> MANUAL_IMPROVED_BREWERIES = Set.of("BRW-033", "BRW-003", "BRW-018");

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
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;
    @Autowired
    private BreweryJoinStatusUpdateService statusUpdateService;
    @Autowired
    private ObjectMapper objectMapper;

    private ProductBreweryJoinService.JoinResult joinResult;

    @BeforeEach
    void loadBreweryOverrideThenJoin() {
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
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
    @DisplayName("MANUAL 개선: override로 새로 연결되는 brewery == {양촌와이너리(BRW-033), 조옥화(BRW-003), 밀과노닐다(BRW-018)}")
    void overrideAddsBreweriesBeyondAuto() {
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
    @DisplayName("합계 커버리지: AUTO∪MANUAL distinct brewery == 59/59")
    void totalLinkedBreweryCoverageIs59() {
        assertThat(joinService.linkedBreweryIds()).hasSize(59);
    }

    @Test
    @DisplayName("AUTO↔override 충돌 0건(같은 product가 서로 다른 brewery로 이중 매칭되는 숨은 오병합 신호 없음)")
    void noAutoOverrideConflicts() {
        assertThat(joinResult.autoOverrideConflicts()).isZero();
    }

    @Test
    @DisplayName("override 14행 실측: 전 행이 실제로 1개 이상 product를 연결(0건 침묵 금지)")
    void everyOverrideActuallyLinksAtLeastOneProduct() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all).hasSize(14);
        for (ManualOverride o : all) {
            int hits = joinResult.overrideHitCounts().getOrDefault(o.getId(), 0);
            assertThat(hits).as("override id=%d (%s, match_key=%s) 실제 연결 건수", o.getId(), o.getOverrideType(), o.getMatchKey())
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("ROW_PIN 재발 방지 가드: 총 6건 = 조옥화 2(BRW-003) + 밀과노닐다 4(BRW-018), 브루어리별 분리 검증")
    void rowPinOverridesLinkJoOkHwaAndMilgwaNonildaProducts() {
        assertThat(joinResult.overrideRowLinked()).isEqualTo(6);

        List<ManualOverride> rowPins = overrideRepository.findAll().stream()
                .filter(o -> o.getOverrideType() == OverrideType.ROW_PIN)
                .toList();
        assertThat(rowPins).hasSize(6);
        // 전체 단정(allSatisfy BRW-003) 금지 — 각 ROW_PIN이 정확히 1건 연결됨은 공통, brewery는 부분집합으로 분리 검증.
        for (ManualOverride rowPin : rowPins) {
            int hits = joinResult.overrideHitCounts().getOrDefault(rowPin.getId(), 0);
            assertThat(hits).as("ROW_PIN match_key=%s 실제 연결 건수(골든 원문 완전일치 필요)", rowPin.getMatchKey())
                    .isEqualTo(1);
        }

        List<ProductBreweryLink> rowPinLinks = linkRepository.findByJoinSource(JoinSource.OVERRIDE_ROW);
        assertThat(rowPinLinks).hasSize(6);

        // ── 조옥화(BRW-003) 재발 방지 가드: 정확히 2건, 골든 원문 제품명 ──
        List<ManualOverride> joOkHwaRowPins = rowPins.stream()
                .filter(o -> o.getBreweryId().equals("BRW-003")).toList();
        assertThat(joOkHwaRowPins).hasSize(2);
        assertThat(joOkHwaRowPins.stream().map(ManualOverride::getMatchKey).sorted().toList())
                .containsExactly("국가유산·명인 조옥화 안동소주 25도", "국가유산·명인 조옥화 안동소주 45도");
        assertThat(rowPinLinks.stream().filter(l -> l.getBreweryId().equals("BRW-003")).count()).isEqualTo(2);

        // ── 밀과노닐다(BRW-018): 정확히 4건(진맥 계열) ──
        List<ManualOverride> milgwaRowPins = rowPins.stream()
                .filter(o -> o.getBreweryId().equals("BRW-018")).toList();
        assertThat(milgwaRowPins).hasSize(4);
        assertThat(milgwaRowPins.stream().map(ManualOverride::getMatchKey).sorted().toList())
                .containsExactly("진맥소주", "진맥소주 시인의바위", "진맥소주 오크40%", "진맥애플");
        assertThat(rowPinLinks.stream().filter(l -> l.getBreweryId().equals("BRW-018")).count()).isEqualTo(4);
    }

    @Test
    @DisplayName("brewery UPDATE 후: JOINED 59 전건, UNJOINED 없음(밀과노닐다 BRW-018 조인 복구 확인)")
    void breweryStatusUpdateYields59JoinedAndNoUnjoined() {
        statusUpdateService.applyJoinResult(joinService.linkedBreweryIds());

        List<Brewery> all = breweryRepository.findAll();
        List<Brewery> joined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.JOINED).toList();
        List<Brewery> unjoined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.UNJOINED).toList();

        assertThat(joined).hasSize(59);
        assertThat(unjoined).isEmpty();
        // 조인 복구 확인: 이전 유일 UNJOINED였던 밀과노닐다(BRW-018)가 이제 JOINED
        assertThat(all.stream().filter(b -> b.getBreweryId().equals(MILGWA_NONILDA_ID)).findFirst())
                .get()
                .satisfies(b -> assertThat(b.getJoinStatus()).isEqualTo(JoinStatus.JOINED));
    }

    @Test
    @DisplayName("liquor_status 2축 정합: JOINED 59 전부 UNTAGGED, UNJOINED 0 (JOINED인데 NA 금지)")
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
    @DisplayName("도수 파싱 골든(link 366 기준): parsed 366 / failed 0, 항등식 parsed+failed==linked, "
            + "min!=max 9건, 전체 min의 최솟값 3.5 / max의 최댓값 61")
    void alcoholParsingGolden() {
        // 카운터: 신규 적재 366행 전부 파싱 성공, 실패 0 (실측 3.5~61이라 fail-fast 미발동)
        assertThat(joinResult.alcoholParsed()).isEqualTo(366);
        assertThat(joinResult.alcoholFailed()).isZero();
        assertThat(joinResult.alcoholParsed() + joinResult.alcoholFailed())
                .as("항등식 parsed+failed==linked")
                .isEqualTo(joinResult.linked());

        // 적재값 골든: 실패 0이므로 366행 min/max 전건 non-null
        List<ProductBreweryLink> links = linkRepository.findAll();
        assertThat(links).hasSize(366);
        for (ProductBreweryLink link : links) {
            assertThat(link.getAlcoholMin()).as("source_row_ref=%d min", link.getSourceRowRef()).isNotNull();
            assertThat(link.getAlcoholMax()).as("source_row_ref=%d max", link.getSourceRowRef()).isNotNull();
        }
        long minNeqMax = links.stream()
                .filter(l -> l.getAlcoholMin().compareTo(l.getAlcoholMax()) != 0)
                .count();
        assertThat(minNeqMax).as("min != max 제품 수").isEqualTo(9);

        BigDecimal globalMin = links.stream()
                .map(ProductBreweryLink::getAlcoholMin).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal globalMax = links.stream()
                .map(ProductBreweryLink::getAlcoholMax).max(BigDecimal::compareTo).orElseThrow();
        assertThat(globalMin).as("전체 min의 최솟값").isEqualByComparingTo("3.5");
        assertThat(globalMax).as("전체 max의 최댓값").isEqualByComparingTo("61");
    }

    @Test
    @DisplayName("도수 백필(#35 결함 재현): 기존 링크(도수 null) 재조인 시 366행 채움, 재실행 멱등(0)")
    void alcoholBackfillFillsExistingNullLinks() {
        // @BeforeEach가 이미 366행을 도수까지 채워 적재함. 증분 조인은 기존 링크를 재적재하지 않으므로,
        // 파이프라인이 이미 돈 기존 DB(도수 컬럼만 뒤늦게 추가된 상태)를 재현하려면 전건을 null로 되돌린다.
        List<ProductBreweryLink> links = linkRepository.findAll();
        assertThat(links).hasSize(366);
        for (ProductBreweryLink link : links) {
            link.backfillAlcohol(null, null);
        }
        linkRepository.saveAll(links);
        long nulledBefore = linkRepository.findAll().stream()
                .filter(l -> l.getAlcoholMin() == null && l.getAlcoholMax() == null).count();
        assertThat(nulledBefore).as("도수 전건 null 되돌림 확인(기존 DB 상태 재현)").isEqualTo(366);

        // 재조인: 신규 적재 0(멱등 skip)이지만 백필 경로로 366행을 채운다(실패 0이라 전건 채워짐)
        ProductBreweryJoinService.JoinResult refill = joinService.join(goldenProductRows());
        assertThat(refill.linked()).as("신규 적재 없음").isZero();
        assertThat(refill.alcoholBackfilled()).as("백필 건수").isEqualTo(366);
        long filledAfter = linkRepository.findAll().stream()
                .filter(l -> l.getAlcoholMin() != null).count();
        assertThat(filledAfter).as("백필 후 alcohol_min 채워진 행").isEqualTo(366);

        // 한 번 더 재조인: 이미 채워져 있으므로 백필 0 (멱등 — 채워진 값은 건드리지 않음)
        ProductBreweryJoinService.JoinResult again = joinService.join(goldenProductRows());
        assertThat(again.alcoholBackfilled()).as("멱등 — 재백필 없음").isZero();
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
