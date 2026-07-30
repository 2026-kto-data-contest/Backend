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
 * 적재 정합 verify(3c-1, manual_override 시드). brewery 선적재 후 시드 9행을 적재한 뒤:
 *   (1) 9행 적재
 *   (2) 타입 분해 정확(NAME_MAP 7 · ROW_PIN 2, BREWERY_NORM 7 · PRODUCT_NAME 2)
 *   (3) 전 행 brewery_id FK가 brewery에 실재
 *   (4) recheck_flag=true 정확히 2행(조옥화 25/45도, PRODUCT_NAME·MANUAL_DOMAIN)
 *   (5) 멱등(재적재 시 신규 0·스킵 9)
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
    private ObjectMapper objectMapper;

    private ManualOverrideSeedLoadService.LoadResult firstResult;

    @BeforeEach
    void loadBreweryThenOverride() {
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        breweryLoadService.load(goldenBreweryAttributeRows());   // FK 대상 선적재
        firstResult = overrideLoadService.load();
    }

    @Test
    @DisplayName("적재 결과: 시드 9행 전부 적재(신규 9·스킵 0)")
    void loadsAll9Rows() {
        assertThat(firstResult.seedRows()).isEqualTo(9);
        assertThat(firstResult.loaded()).isEqualTo(9);
        assertThat(firstResult.skippedExisting()).isZero();
        assertThat(overrideRepository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("타입 분해: NAME_MAP 7 · ROW_PIN 2, BREWERY_NORM 7 · PRODUCT_NAME 2")
    void typeSplitIsExact() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all.stream().filter(o -> o.getOverrideType() == OverrideType.NAME_MAP).count()).isEqualTo(7);
        assertThat(all.stream().filter(o -> o.getOverrideType() == OverrideType.ROW_PIN).count()).isEqualTo(2);
        assertThat(all.stream().filter(o -> o.getMatchKeyKind() == MatchKeyKind.BREWERY_NORM).count()).isEqualTo(7);
        assertThat(all.stream().filter(o -> o.getMatchKeyKind() == MatchKeyKind.PRODUCT_NAME).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("FK 유효: 전 행 brewery_id가 brewery에 실재(6개 BRW)")
    void allForeignKeysResolve() {
        List<ManualOverride> all = overrideRepository.findAll();
        assertThat(all).allSatisfy(o ->
                assertThat(breweryRepository.existsById(o.getBreweryId()))
                        .as("FK %s 실재", o.getBreweryId()).isTrue());
        assertThat(all.stream().map(ManualOverride::getBreweryId).distinct().sorted().toList())
                .containsExactly("BRW-001", "BRW-003", "BRW-025", "BRW-033", "BRW-037", "BRW-053");
    }

    @Test
    @DisplayName("recheck_flag: 정확히 2행 true(조옥화 25/45도, ROW_PIN·PRODUCT_NAME·MANUAL_DOMAIN)")
    void recheckRowsAreExactlyTheTwoJoOkHwa() {
        List<ManualOverride> recheck = overrideRepository.findAll().stream()
                .filter(ManualOverride::isRecheckFlag).toList();
        assertThat(recheck).hasSize(2);
        assertThat(recheck).allSatisfy(o -> {
            assertThat(o.getOverrideType()).isEqualTo(OverrideType.ROW_PIN);
            assertThat(o.getMatchKeyKind()).isEqualTo(MatchKeyKind.PRODUCT_NAME);
            assertThat(o.getReason()).isEqualTo(OverrideReason.MANUAL_DOMAIN);
            assertThat(o.getBreweryId()).isEqualTo("BRW-003");
        });
        assertThat(recheck.stream().map(ManualOverride::getMatchKey).sorted().toList())
                .containsExactly("조옥화 안동소주 25도", "조옥화 안동소주 45도");
    }

    @Test
    @DisplayName("멱등: 재적재 시 신규 0·스킵 9, 행수 불변")
    void reloadIsIdempotent() {
        ManualOverrideSeedLoadService.LoadResult again = overrideLoadService.load();
        assertThat(again.loaded()).isZero();
        assertThat(again.skippedExisting()).isEqualTo(9);
        assertThat(overrideRepository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("FK 미충족 시 멈춤: brewery 비운 뒤 적재 시도 → 예외(누락 BRW 보고)")
    void stopsWhenBreweryMissing() {
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
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
