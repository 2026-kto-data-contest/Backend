package com.jeontongjuro.backend.liquortype;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryJoinStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryLiquorStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.JoinStatus;
import com.jeontongjuro.backend.brewery.LiquorStatus;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.product.ProductBreweryJoinService;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.time.LocalDate;
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
 * 주종 롤업 end-to-end verify(로컬 PostgreSQL 필요). 골든 raw를 적재 → 마스터/override/조인으로 link를 만든 뒤
 * 주종 추론을 돌려 규칙 결정론·멱등·MANUAL wins·향미이중 억제·미커버 무태깅·기존 골든 불변을 검증한다.
 * <p>
 * ★분포 골든 단정 금지 — 특정 제품의 규칙 산출과 불변식만 확인한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 주종 롤업 verify 스킵")
class LiquorRollupIntegrationTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 28);

    @Autowired
    private RawCollectService collectService;
    @Autowired
    private BreweryMasterLoadService masterLoadService;
    @Autowired
    private ManualOverrideSeedLoadService overrideSeedLoadService;
    @Autowired
    private ProductBreweryJoinService joinService;
    @Autowired
    private BreweryJoinStatusUpdateService joinStatusUpdateService;
    @Autowired
    private LiquorManualSeedLoadService liquorManualSeedLoadService;
    @Autowired
    private LiquorInferenceService liquorInferenceService;
    @Autowired
    private LiquorRollupStatusService liquorRollupStatusService;
    @Autowired
    private BreweryLiquorStatusUpdateService liquorStatusUpdateService;
    @Autowired
    private LiquorAuditReportService liquorAuditReportService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductLiquorTypeRepository liquorTypeRepository;
    @Autowired
    private BreweryRawRepository breweryRawRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedThroughJoin() {
        liquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        breweryRawRepository.deleteAll();
        productRawRepository.deleteAll();

        FixtureRawSnapshotSource source = new FixtureRawSnapshotSource(objectMapper);
        collectService.collect(source.fetch(RawDataset.BREWERY), SNAPSHOT);
        collectService.collect(source.fetch(RawDataset.PRODUCT), SNAPSHOT);

        // link까지만 준비(주종 추론은 각 테스트가 통제)
        masterLoadService.load(breweryRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(SNAPSHOT));
        overrideSeedLoadService.load();
        joinService.join(productRaws());
        joinStatusUpdateService.applyJoinResult(joinService.linkedBreweryIds());
    }

    private List<ProductRaw> productRaws() {
        return productRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(SNAPSHOT);
    }

    /** link의 (source_row_ref → product_name) 매핑. */
    private Map<Integer, String> productNameByRowRef() {
        Map<Integer, String> m = new HashMap<>();
        for (ProductBreweryLink l : linkRepository.findAll()) {
            m.put(l.getSourceRowRef(), l.getProductName());
        }
        return m;
    }

    /** 특정 제품명(정확일치)에 달린 태그들. */
    private List<ProductLiquorType> tagsForProduct(String productName) {
        Map<Integer, String> names = productNameByRowRef();
        return liquorTypeRepository.findAll().stream()
                .filter(t -> productName.equals(names.get(t.getSourceRowRef())))
                .toList();
    }

    /** 특정 제품명의 링크 1건(source_row_ref). */
    private ProductBreweryLink linkFor(String productName) {
        return linkRepository.findAll().stream()
                .filter(l -> productName.equals(l.getProductName()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("규칙 결정론: '유자생막걸리'는 탁주로 태깅되고 향미이중으로 억제 표시된다")
    void yuzaMakgeolliIsTakjuAndSuppressed() {
        liquorInferenceService.infer(productRaws());

        List<ProductLiquorType> tags = tagsForProduct("유자생막걸리");
        assertThat(tags).anySatisfy(t -> {
            assertThat(t.getLiquorType()).isEqualTo(LiquorType.탁주);
            assertThat(t.getSource()).isEqualTo(LiquorTagSource.AUTO);
            assertThat(t.isRecheckFlag()).isTrue();
            assertThat(t.isSuppressedFromTab()).isTrue();     // 유자(향미)+막걸리(주종) 동시
            assertThat(t.getMatchedKeyword()).isEqualTo("막걸리");
        });
    }

    @Test
    @DisplayName("향미이중: '추사백40 사과 증류식 소주'(사과+소주)·'머루와인'(머루+와인)은 태깅+억제 표시")
    void flavorDoublesAreSuppressed() {
        liquorInferenceService.infer(productRaws());

        assertThat(tagsForProduct("추사백40 사과 증류식 소주")).anySatisfy(t -> {
            assertThat(t.getLiquorType()).isEqualTo(LiquorType.증류주);
            assertThat(t.isSuppressedFromTab()).isTrue();
        });
        assertThat(tagsForProduct("무주구천동머루와인")).anySatisfy(t -> {
            assertThat(t.getLiquorType()).isEqualTo(LiquorType.과실주);
            assertThat(t.isSuppressedFromTab()).isTrue();
        });
    }

    @Test
    @DisplayName("단어경계: '진맥소주'는 '진'(gin)으로 태깅되지 않는다(증류주는 소주/증류 근거)")
    void jinmaekSojuNotTaggedByGin() {
        liquorInferenceService.infer(productRaws());

        List<ProductLiquorType> tags = tagsForProduct("진맥소주");
        assertThat(tags).isNotEmpty();
        assertThat(tags).allSatisfy(t -> assertThat(t.getMatchedKeyword()).isNotEqualTo("진"));
        assertThat(tags).anySatisfy(t -> assertThat(t.getLiquorType()).isEqualTo(LiquorType.증류주));
    }

    @Test
    @DisplayName("미커버·무태깅: AUTO는 '기타'를 만들지 않고, 미커버 양조장은 존재하며 태그가 0건이다")
    void uncoveredBreweriesGetNoOtherRow() {
        LiquorInferenceService.InferResult result = liquorInferenceService.infer(productRaws());

        // AUTO는 기타를 만들지 않는다(판정 안 됨 ≠ 기타)
        assertThat(liquorTypeRepository.findAll())
                .noneSatisfy(t -> assertThat(t.getLiquorType()).isEqualTo(LiquorType.기타));

        // 미커버 양조장이 실제로 존재(강제 커버하지 않음)
        assertThat(result.uncoveredProducts()).isGreaterThan(0);
        Map<String, Long> tagCountByBrewery = new HashMap<>();
        liquorTypeRepository.findAll().forEach(t ->
                tagCountByBrewery.merge(t.getBreweryId(), 1L, Long::sum));
        long linkedBreweries = breweryRepository.findAll().stream()
                .filter(b -> b.getJoinStatus().name().equals("JOINED")).count();
        assertThat(tagCountByBrewery.size()).isLessThan((int) linkedBreweries); // 태깅된 양조장 < 전체 → 미커버 존재
    }

    @Test
    @DisplayName("멱등: 2회 추론 시 2회차 신규 0·전건 skip, 총 태그 행수 불변")
    void inferenceIsIdempotent() {
        LiquorInferenceService.InferResult first = liquorInferenceService.infer(productRaws());
        long after1 = liquorTypeRepository.count();

        LiquorInferenceService.InferResult second = liquorInferenceService.infer(productRaws());

        assertThat(first.tagRowsInserted()).isGreaterThan(0);
        assertThat(second.tagRowsInserted()).isZero();
        assertThat(second.skippedExisting()).isEqualTo(first.tagRowsInserted());
        assertThat(liquorTypeRepository.count()).isEqualTo(after1);
    }

    @Test
    @DisplayName("MANUAL 우선: 같은 (제품,주종)에 MANUAL이 있으면 AUTO가 이기지 못한다(1행·source=MANUAL)")
    void manualWinsOverAuto() {
        ProductBreweryLink link = linkFor("유자생막걸리");
        liquorTypeRepository.save(
                ProductLiquorType.manual(link.getSourceRowRef(), link.getBreweryId(), LiquorType.탁주));

        liquorInferenceService.infer(productRaws());

        List<ProductLiquorType> takjuTags = liquorTypeRepository.findAll().stream()
                .filter(t -> t.getSourceRowRef().equals(link.getSourceRowRef()))
                .filter(t -> t.getLiquorType() == LiquorType.탁주)
                .toList();
        assertThat(takjuTags).hasSize(1);
        assertThat(takjuTags.get(0).getSource()).isEqualTo(LiquorTagSource.MANUAL);
        assertThat(takjuTags.get(0).isRecheckFlag()).isFalse();
    }

    /** #18 2차 시드 대상 미커버 양조장 10곳(전건 MANUAL로 확정 → TAGGED 전이 예상). */
    private static final List<String> SEEDED_BREWERIES = List.of(
            "BRW-007", "BRW-009", "BRW-010", "BRW-014", "BRW-016",
            "BRW-019", "BRW-025", "BRW-041", "BRW-043", "BRW-054");

    @Test
    @DisplayName("MANUAL 시드(2차): 29건 적재·전건 source=MANUAL·recheck_flag=false·matched_keyword=null")
    void manualSeedLoads29AsConfirmed() {
        LiquorManualSeedLoadService.LoadResult result = liquorManualSeedLoadService.load();
        assertThat(result.seedRows()).isEqualTo(29);   // 전사 드리프트 감지(요약 STEP 2d)
        assertThat(result.loaded()).isEqualTo(29);
        assertThat(result.skippedExisting()).isZero();

        List<ProductLiquorType> manual = liquorTypeRepository.findAll().stream()
                .filter(t -> t.getSource() == LiquorTagSource.MANUAL)
                .toList();
        assertThat(manual).hasSize(29);
        assertThat(manual).allSatisfy(t -> {
            assertThat(t.isRecheckFlag()).isFalse();
            assertThat(t.isSuppressedFromTab()).isFalse();
            assertThat(t.getMatchedKeyword()).isNull();
        });
    }

    @Test
    @DisplayName("7단계 전이: 시드 10곳(전건 검수완료)만 TAGGED, 나머지 JOINED는 UNTAGGED, NA 0(2축 유지)")
    void liquorStatusTransitionsForConfirmedBreweries() {
        liquorManualSeedLoadService.load();
        liquorInferenceService.infer(productRaws());
        liquorStatusUpdateService.applyLiquorRollup(liquorRollupStatusService.confirmedBreweryIds());

        Map<String, LiquorStatus> statusById = new HashMap<>();
        breweryRepository.findAll().forEach(b -> statusById.put(b.getBreweryId(), b.getLiquorStatus()));

        // 시드 10곳: 조인 제품이 전건 MANUAL(recheck=false)로 채워져 TAGGED
        for (String id : SEEDED_BREWERIES) {
            assertThat(statusById.get(id)).as("시드 양조장 TAGGED: %s", id).isEqualTo(LiquorStatus.TAGGED);
        }
        // 그 외 JOINED 양조장: AUTO(recheck=true) 또는 미태깅 → UNTAGGED. NA는 없어야(전건 JOINED)
        breweryRepository.findAll().forEach(b -> {
            if (b.getJoinStatus() == JoinStatus.JOINED && !SEEDED_BREWERIES.contains(b.getBreweryId())) {
                assertThat(b.getLiquorStatus()).as("비시드 JOINED UNTAGGED: %s", b.getBreweryId())
                        .isEqualTo(LiquorStatus.UNTAGGED);
            }
        });
        assertThat(statusById.values()).doesNotContain(LiquorStatus.NA); // 2축: JOINED 59 전건이라 NA 0
    }

    @Test
    @DisplayName("7단계 규칙 불변: 모든 TAGGED 양조장은 조인 제품 전건 태깅 & 전건 recheck_flag=false")
    void taggedBreweriesSatisfyRule() {
        liquorManualSeedLoadService.load();
        liquorInferenceService.infer(productRaws());
        liquorStatusUpdateService.applyLiquorRollup(liquorRollupStatusService.confirmedBreweryIds());

        // brewery_id -> 조인 제품 refs
        Map<String, List<Integer>> refsByBrewery = new HashMap<>();
        linkRepository.findAll().forEach(l -> {
            if (l.getBreweryId() != null) {
                refsByBrewery.computeIfAbsent(l.getBreweryId(), k -> new java.util.ArrayList<>())
                        .add(l.getSourceRowRef());
            }
        });
        Map<Integer, List<ProductLiquorType>> tagsByRef = new HashMap<>();
        liquorTypeRepository.findAll().forEach(t ->
                tagsByRef.computeIfAbsent(t.getSourceRowRef(), k -> new java.util.ArrayList<>()).add(t));

        breweryRepository.findAll().stream()
                .filter(b -> b.getLiquorStatus() == LiquorStatus.TAGGED)
                .forEach(b -> {
                    List<Integer> refs = refsByBrewery.getOrDefault(b.getBreweryId(), List.of());
                    assertThat(refs).as("TAGGED는 조인 제품이 있어야: %s", b.getBreweryId()).isNotEmpty();
                    for (Integer ref : refs) {
                        List<ProductLiquorType> tags = tagsByRef.get(ref);
                        assertThat(tags).as("TAGGED의 조인 제품 전건 태깅: %s ref=%s", b.getBreweryId(), ref)
                                .isNotEmpty();
                        assertThat(tags).allSatisfy(t -> assertThat(t.isRecheckFlag())
                                .as("TAGGED의 태그 전건 recheck=false: %s ref=%s", b.getBreweryId(), ref)
                                .isFalse());
                    }
                });
    }

    @Test
    @DisplayName("7단계 멱등: 상태 전이 2회차는 changed=0·전건 unchanged, 값 불변")
    void liquorStatusTransitionIsIdempotent() {
        liquorManualSeedLoadService.load();
        liquorInferenceService.infer(productRaws());

        BreweryLiquorStatusUpdateService.UpdateResult first =
                liquorStatusUpdateService.applyLiquorRollup(liquorRollupStatusService.confirmedBreweryIds());
        assertThat(first.changed()).isGreaterThan(0);       // 1회차: UNTAGGED→TAGGED 전이 발생

        BreweryLiquorStatusUpdateService.UpdateResult second =
                liquorStatusUpdateService.applyLiquorRollup(liquorRollupStatusService.confirmedBreweryIds());
        assertThat(second.changed()).isZero();
        assertThat(second.unchanged()).isEqualTo(second.total());
    }

    @Test
    @DisplayName("조회 집계: onlyConfirmed=true는 비어 있고(전건 recheck=true), false는 결과가 있다")
    void aggregateFilterRespectsRecheckFlag() {
        liquorInferenceService.infer(productRaws());

        assertThat(liquorTypeRepository.aggregateByBreweryAndType(true)).isEmpty();
        assertThat(liquorTypeRepository.aggregateByBreweryAndType(false)).isNotEmpty();
    }

    @Test
    @DisplayName("기존 골든 불변: 추론이 brewery 59·link 366·override 14·liquor_status(UNTAGGED)를 바꾸지 않는다")
    void existingGoldenIsUnchanged() {
        long breweryBefore = breweryRepository.count();
        long linkBefore = linkRepository.count();
        long overrideBefore = overrideRepository.count();

        liquorInferenceService.infer(productRaws());

        assertThat(breweryRepository.count()).isEqualTo(breweryBefore).isEqualTo(59);
        assertThat(linkRepository.count()).isEqualTo(linkBefore).isEqualTo(366);
        assertThat(overrideRepository.count()).isEqualTo(overrideBefore).isEqualTo(14);
        // 주종 롤업은 liquor_status를 전이시키지 않는다(2차 범위). 조인 승격분은 UNTAGGED 그대로.
        assertThat(breweryRepository.findAll())
                .noneSatisfy(b -> assertThat(b.getLiquorStatus()).isEqualTo(LiquorStatus.TAGGED));
    }

    @Test
    @DisplayName("검수 리포트: 3섹션 헤더와 미커버 양조장이 렌더링된다")
    void auditReportHasThreeSections() {
        liquorInferenceService.infer(productRaws());
        String report = liquorAuditReportService.render(SNAPSHOT);

        assertThat(report).contains("(1) 미커버 양조장");
        assertThat(report).contains("(2) 향미이중 후보");
        assertThat(report).contains("(3) 무태깅 제품");
        assertThat(report).contains("source_row_ref");
    }
}
