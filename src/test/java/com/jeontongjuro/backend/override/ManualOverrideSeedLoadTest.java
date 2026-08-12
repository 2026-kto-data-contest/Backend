package com.jeontongjuro.backend.override;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 적재 정합 verify(3c-1, manual_override 시드). brewery 선적재 후 시드 14행을 적재한 뒤:
 *   (1) 14행 적재
 *   (2) 타입 분해 정확(NAME_MAP 8 · ROW_PIN 6, BREWERY_NORM 8 · PRODUCT_NAME 6)
 *   (3) 전 행 brewery_id FK가 brewery에 실재
 *   (4) recheck_flag=true 대상: 조옥화 2행(BRW-003) + 밀과노닐다 5행(BRW-018) 부분집합 검증
 *   (5) 멱등(재적재 시 신규 0·스킵 14)
 *   (6) FK 미충족 시 멈춤(brewery 없이 적재 시도 → 예외)
 * ★조인·커버리지·주종 카운트는 검증하지 않는다(3c-2). 적재만.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — override 적재 verify 스킵")
class ManualOverrideSeedLoadTest {

    @Autowired
    private ManualOverrideSeedLoadService overrideLoadService;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private BreweryMasterLoadService breweryLoadService;
    @Autowired
    private BreweryRepository breweryRepository;
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

    private ManualOverrideSeedLoadService.LoadResult firstResult;

    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.experience.BreweryExperienceRepository experienceRepository;

    @BeforeEach
    void loadBreweryThenOverride() {
        // FK 자식(product_brewery_link)이 brewery를 참조하므로 자식 먼저 비운 뒤 override·brewery 비우고 재적재
        experienceRepository.deleteAll();   // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        breweryLoadService.load(goldenBreweryAttributeRows());   // FK 대상 선적재
        firstResult = overrideLoadService.load();
    }

    @Test
    @DisplayName("적재 결과: 시드 14행 전부 적재(신규 14·스킵 0)")
    void loadsAll14Rows() {
        assertThat(firstResult.seedRows()).isEqualTo(14);
        assertThat(firstResult.loaded()).isEqualTo(14);
        assertThat(firstResult.skippedExisting()).isZero();
        assertThat(overrideRepository.count()).isEqualTo(14);
    }

    @Test
    @DisplayName("타입 분해: NAME_MAP 8 · ROW_PIN 6, BREWERY_NORM 8 · PRODUCT_NAME 6")
    void typeSplitIsExact() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all.stream().filter(o -> o.getOverrideType() == OverrideType.NAME_MAP).count()).isEqualTo(8);
        assertThat(all.stream().filter(o -> o.getOverrideType() == OverrideType.ROW_PIN).count()).isEqualTo(6);
        assertThat(all.stream().filter(o -> o.getMatchKeyKind() == MatchKeyKind.BREWERY_NORM).count()).isEqualTo(8);
        assertThat(all.stream().filter(o -> o.getMatchKeyKind() == MatchKeyKind.PRODUCT_NAME).count()).isEqualTo(6);
    }

    @Test
    @DisplayName("FK 유효: 전 행 brewery_id가 brewery에 실재(7개 BRW)")
    void allForeignKeysResolve() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all).allSatisfy(o ->
                assertThat(breweryRepository.existsById(o.getBreweryId()))
                        .as("FK %s 실재", o.getBreweryId()).isTrue());
        assertThat(all.stream().map(ManualOverride::getBreweryId).distinct().sorted().toList())
                .containsExactly("BRW-001", "BRW-003", "BRW-018", "BRW-025", "BRW-033", "BRW-037", "BRW-053");
    }

    @Test
    @DisplayName("recheck_flag: true 7행 = 조옥화 2(BRW-003) + 밀과노닐다 5(BRW-018), 브루어리별 분리 검증")
    void recheckRowsCoverJoOkHwaAndMilgwaNonilda() {
        List<ManualOverride> recheck = overrideRepository.findAll().stream()
                .filter(ManualOverride::isRecheckFlag).toList();
        // 전체 단정(containsExactly) 금지 — 미래에 세 번째 recheck 대상이 늘어도 카운트만 바꾸면 되게 부분집합으로.
        assertThat(recheck).hasSize(7);

        // ── 조옥화(BRW-003) 재발 방지 가드: 정확히 2행, ROW_PIN·PRODUCT_NAME·MANUAL_DOMAIN, 골든 원문 제품명 ──
        List<ManualOverride> joOkHwa = recheck.stream()
                .filter(o -> o.getBreweryId().equals("BRW-003")).toList();
        assertThat(joOkHwa).hasSize(2);
        assertThat(joOkHwa).allSatisfy(o -> {
            assertThat(o.getOverrideType()).isEqualTo(OverrideType.ROW_PIN);
            assertThat(o.getMatchKeyKind()).isEqualTo(MatchKeyKind.PRODUCT_NAME);
            assertThat(o.getReason()).isEqualTo(OverrideReason.MANUAL_DOMAIN);
        });
        assertThat(joOkHwa.stream().map(ManualOverride::getMatchKey).sorted().toList())
                .containsExactly("국가유산·명인 조옥화 안동소주 25도", "국가유산·명인 조옥화 안동소주 45도");

        // ── 밀과노닐다(BRW-018): 정확히 5행 = NAME_MAP 1(맹개술) + ROW_PIN 4(진맥 계열) ──
        List<ManualOverride> milgwaNonilda = recheck.stream()
                .filter(o -> o.getBreweryId().equals("BRW-018")).toList();
        assertThat(milgwaNonilda).hasSize(5);
        assertThat(milgwaNonilda).allSatisfy(o ->
                assertThat(o.getReason()).isEqualTo(OverrideReason.MANUAL_DOMAIN));
        assertThat(milgwaNonilda.stream()
                .filter(o -> o.getOverrideType() == OverrideType.NAME_MAP).count()).isEqualTo(1);
        assertThat(milgwaNonilda.stream()
                .filter(o -> o.getOverrideType() == OverrideType.ROW_PIN).count()).isEqualTo(4);
        assertThat(milgwaNonilda.stream().map(ManualOverride::getMatchKey).sorted().toList())
                .contains("맹개술", "진맥소주", "진맥소주 시인의바위", "진맥소주 오크40%", "진맥애플");
    }

    @Test
    @DisplayName("멱등: 재적재 시 신규 0·스킵 14, 행수 불변")
    void reloadIsIdempotent() {
        ManualOverrideSeedLoadService.LoadResult again = overrideLoadService.load();
        assertThat(again.loaded()).isZero();
        assertThat(again.skippedExisting()).isEqualTo(14);
        assertThat(overrideRepository.count()).isEqualTo(14);
    }

    @Test
    @DisplayName("FK 미충족 시 멈춤: brewery 비운 뒤 적재 시도 → 예외(누락 BRW 보고)")
    void stopsWhenBreweryMissing() {
        experienceRepository.deleteAll();   // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        assertThatThrownBy(() -> overrideLoadService.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FK 위반");
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
