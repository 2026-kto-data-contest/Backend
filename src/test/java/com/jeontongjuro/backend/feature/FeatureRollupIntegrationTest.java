package com.jeontongjuro.backend.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryJoinStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.product.ProductBreweryJoinService;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 특징 롤업 end-to-end verify(이슈 #43, 로컬 PostgreSQL 필요). 골든 raw를 적재 → 마스터/override/조인으로 link를
 * 만든 뒤 특징 롤업을 돌려 <b>골든 분포(수상이력 36·식품명인 9·유기농 7·무형문화재 3·대통령상 2)</b>와 삭제형 diff의
 * 삽입/삭제/멱등, 오탐 경계를 검증한다.
 * <p>
 * ★골든 근거: live(snapshot 2026-08-01)와 픽스처(2026-07-28)의 제품 raw가 동일(RAW 키워드 행수 181/44/46/21/3
 * 일치 실측)하고 조인이 결정론적(366 링크/59 양조장)이라, 이 하니스가 live 골든을 그대로 재현한다.
 * 골든은 확정값이다 — 재계산이 아니라 비교 기준으로만 쓴다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 특징 롤업 verify 스킵")
class FeatureRollupIntegrationTest {

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
    private FeatureRollupService featureRollupService;
    @Autowired
    private BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository liquorTypeRepository;
    @Autowired
    private BreweryRawRepository breweryRawRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.experience.BreweryExperienceRepository experienceRepository;

    @BeforeEach
    void seedThroughJoin() {
        experienceRepository.deleteAll();   // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        liquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        breweryRawRepository.deleteAll();
        productRawRepository.deleteAll();

        FixtureRawSnapshotSource source = new FixtureRawSnapshotSource(objectMapper);
        collectService.collect(source.fetch(RawDataset.BREWERY), SNAPSHOT);
        collectService.collect(source.fetch(RawDataset.PRODUCT), SNAPSHOT);

        // link까지만 준비(특징 롤업은 각 테스트가 통제)
        masterLoadService.load(breweryRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(SNAPSHOT));
        overrideSeedLoadService.load();
        joinService.join(productRaws());
        joinStatusUpdateService.applyJoinResult(joinService.linkedBreweryIds());
    }

    private List<ProductRaw> productRaws() {
        return productRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(SNAPSHOT);
    }

    /** feature_type별 태그를 보유한 distinct 양조장 수. */
    private Map<FeatureType, Long> distinctBreweriesByType() {
        return featureTagRepository.findAll().stream()
                .collect(Collectors.groupingBy(BreweryFeatureTag::getFeatureType,
                        Collectors.mapping(BreweryFeatureTag::getBreweryId,
                                Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));
    }

    @Test
    @DisplayName("골든 분포: 수상이력 36·식품명인 9·유기농 7·무형문화재 3·대통령상 2 (DISTINCT brewery_id)")
    void goldenDistribution() {
        featureRollupService.rollup(productRaws());

        Map<FeatureType, Long> byType = distinctBreweriesByType();
        assertThat(byType.getOrDefault(FeatureType.수상이력, 0L)).isEqualTo(36);
        assertThat(byType.getOrDefault(FeatureType.식품명인, 0L)).isEqualTo(9);
        assertThat(byType.getOrDefault(FeatureType.유기농, 0L)).isEqualTo(7);
        assertThat(byType.getOrDefault(FeatureType.무형문화재, 0L)).isEqualTo(3);
        assertThat(byType.getOrDefault(FeatureType.대통령상, 0L)).isEqualTo(2);
        // (brewery_id, feature_type)이 uq라 총 태그 행수 = 분포 합(중복 없음)
        assertThat(featureTagRepository.count()).isEqualTo(36 + 9 + 7 + 3 + 2);
    }

    @Test
    @DisplayName("오탐 경계: 대통령상은 '대통령상' 근거만(BRW-058 '대통령표창'은 대통령상 미태깅)")
    void presidentAwardIsNotBroadened() {
        featureRollupService.rollup(productRaws());

        List<String> presidentBreweries = featureTagRepository.findAll().stream()
                .filter(t -> t.getFeatureType() == FeatureType.대통령상)
                .map(BreweryFeatureTag::getBreweryId)
                .toList();
        // 정확히 2곳(BRW-016·BRW-028), 표창만 있는 BRW-058은 없음
        assertThat(presidentBreweries).containsExactlyInAnyOrder("BRW-016", "BRW-028");
        assertThat(presidentBreweries).doesNotContain("BRW-058");
    }

    @Test
    @DisplayName("근거 무결성: 모든 태그의 source_row_ref는 link로 연결된 제품이고, 키워드 규칙과 일치한다")
    void tagsCarryValidEvidence() {
        featureRollupService.rollup(productRaws());

        Map<Integer, String> breweryIdByRowRef = linkRepository.findAll().stream()
                .filter(l -> l.getBreweryId() != null)
                .collect(Collectors.toMap(ProductBreweryLink::getSourceRowRef, ProductBreweryLink::getBreweryId));

        assertThat(featureTagRepository.findAll()).allSatisfy(t -> {
            // 대표 제품은 그 양조장에 연결된 제품이어야 한다
            assertThat(breweryIdByRowRef.get(t.getSourceRowRef())).isEqualTo(t.getBreweryId());
            // 키워드 기반 특징은 해당 키워드를, 수상이력은 presence라 null을 담는다
            switch (t.getFeatureType()) {
                case 수상이력 -> assertThat(t.getMatchedKeyword()).isNull();
                case 식품명인 -> assertThat(t.getMatchedKeyword()).isEqualTo("식품명인");
                case 유기농 -> assertThat(t.getMatchedKeyword()).isEqualTo("유기농");
                case 무형문화재 -> assertThat(t.getMatchedKeyword()).isEqualTo("무형문화재");
                case 대통령상 -> assertThat(t.getMatchedKeyword()).isEqualTo("대통령상");
                default -> { }
            }
        });
    }

    @Test
    @DisplayName("멱등: 2회 롤업 시 2회차는 inserted=deleted=0·전건 unchanged, 총 행수·분포 불변")
    void rollupIsIdempotent() {
        FeatureRollupService.RollupResult first = featureRollupService.rollup(productRaws());
        long after1 = featureTagRepository.count();
        Map<FeatureType, Long> dist1 = distinctBreweriesByType();

        FeatureRollupService.RollupResult second = featureRollupService.rollup(productRaws());

        assertThat(first.inserted()).isGreaterThan(0);
        assertThat(first.deleted()).isZero();
        assertThat(second.inserted()).isZero();
        assertThat(second.deleted()).isZero();
        assertThat(second.unchanged()).isEqualTo(first.inserted());
        assertThat(featureTagRepository.count()).isEqualTo(after1);
        assertThat(distinctBreweriesByType()).isEqualTo(dist1);
    }

    @Test
    @DisplayName("삭제형 diff(부채 #11 방지): DELETE→복원(inserted>0)·유령 삽입→제거(deleted>0)·재실행 멱등(0/0)")
    void deleteDiffRestoresAndPrunes() {
        // 1) 최초 적재
        featureRollupService.rollup(productRaws());
        long baseline = featureTagRepository.count();
        assertThat(baseline).isGreaterThan(0);

        // 2) 원본 갱신으로 태그가 사라진 상황 모사 — 일부 태그 DELETE
        Map<FeatureType, BreweryFeatureTag> oneEach = featureTagRepository.findAll().stream()
                .collect(Collectors.toMap(BreweryFeatureTag::getFeatureType, Function.identity(),
                        (a, b) -> a));
        List<BreweryFeatureTag> removed = List.copyOf(oneEach.values());
        featureTagRepository.deleteAll(removed);
        assertThat(featureTagRepository.count()).isEqualTo(baseline - removed.size());

        // 재실행 → 삭제분만 복원(inserted>0), 유령 삭제 0
        FeatureRollupService.RollupResult restore = featureRollupService.rollup(productRaws());
        assertThat(restore.inserted()).isEqualTo(removed.size());
        assertThat(restore.deleted()).isZero();
        assertThat(featureTagRepository.count()).isEqualTo(baseline);

        // 3) 규칙이 만들지 않는 유령 (양조장,특징) 삽입 — 규칙상 특징이 0개인 양조장을 골라 수상이력 부여
        String ghostBrewery = pickBreweryWithoutAnyFeature();
        featureTagRepository.save(BreweryFeatureTag.of(ghostBrewery, FeatureType.수상이력, 999999, null));
        assertThat(featureTagRepository.count()).isEqualTo(baseline + 1);

        // 재실행 → 유령만 삭제(deleted>0), 삽입 0
        FeatureRollupService.RollupResult prune = featureRollupService.rollup(productRaws());
        assertThat(prune.deleted()).isEqualTo(1);
        assertThat(prune.inserted()).isZero();
        assertThat(featureTagRepository.count()).isEqualTo(baseline);

        // 4) 재실행 멱등
        FeatureRollupService.RollupResult stable = featureRollupService.rollup(productRaws());
        assertThat(stable.inserted()).isZero();
        assertThat(stable.deleted()).isZero();
        assertThat(featureTagRepository.count()).isEqualTo(baseline);
    }

    @Test
    @DisplayName("기존 골든 불변: 특징 롤업이 brewery 59·link 366·override 14를 바꾸지 않는다")
    void existingGoldenIsUnchanged() {
        long breweryBefore = breweryRepository.count();
        long linkBefore = linkRepository.count();
        long overrideBefore = overrideRepository.count();

        featureRollupService.rollup(productRaws());

        assertThat(breweryRepository.count()).isEqualTo(breweryBefore).isEqualTo(59);
        assertThat(linkRepository.count()).isEqualTo(linkBefore).isEqualTo(366);
        assertThat(overrideRepository.count()).isEqualTo(overrideBefore).isEqualTo(14);
    }

    /** 규칙상 어떤 특징에도 걸리지 않는(태그 0개) 연결 양조장 하나 — 유령 삽입 대상. */
    private String pickBreweryWithoutAnyFeature() {
        return linkRepository.findAll().stream()
                .map(ProductBreweryLink::getBreweryId)
                .filter(id -> id != null)
                .distinct()
                .filter(id -> featureTagRepository.findByBreweryIdIn(List.of(id)).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("태그 0개 양조장이 없어 유령 삽입 테스트 불가"));
    }
}
