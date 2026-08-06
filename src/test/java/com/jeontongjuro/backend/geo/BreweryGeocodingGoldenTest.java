package com.jeontongjuro.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.process.ProcessOrchestrator;
import com.jeontongjuro.backend.pipeline.process.ProcessReport;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.testsupport.StubGeocodingConfig;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 지오코딩 8단계 오케스트레이터 검증(#28 단정 5·6, 로컬 PostgreSQL 필요). 카카오는 스텁으로 대체한다
 * ({@link StubGeocodingConfig}) — 실제 호출 없이 멱등과 address 무오염을 검증한다.
 * <ul>
 *   <li>단정5: 8단계 실행 후 {@code brewery.address} 재파싱 지역 8칩 == 골든(13/17/8/14/3/2/1/1) —
 *       좌표 편입이 address를 오염시키지 않았음을 실증.</li>
 *   <li>단정6: 2회 실행 시 {@code geocoded=0, skipped=59} — 좌표 있으면 카카오 미호출(멱등).</li>
 * </ul>
 * (단정 1~4의 실측 분포·시드 실좌표는 실기동 산물 골든 TSV로 {@link BreweryCoordGoldenTest}가 검증.)
 */
@SpringBootTest
@Import(StubGeocodingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동 — 지오코딩 오케스트레이터 verify 스킵")
class BreweryGeocodingGoldenTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 28);

    /** region 8칩 골든 분포(회귀 기준선 — address 오염 감지). */
    private static final Map<String, Integer> GOLDEN_CHIP = Map.of(
            "수도권", 13, "충청", 17, "전라", 8, "경상", 14, "강원", 3, "제주", 2, "부산", 1, "울산", 1);

    @Autowired
    private ProcessOrchestrator orchestrator;
    @Autowired
    private RawCollectService collectService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private BreweryRawRepository breweryRawRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetAndSeedRaw() {
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        breweryRawRepository.deleteAll();
        productRawRepository.deleteAll();
        FixtureRawSnapshotSource source = new FixtureRawSnapshotSource(objectMapper);
        collectService.collect(source.fetch(RawDataset.BREWERY), SNAPSHOT);
        collectService.collect(source.fetch(RawDataset.PRODUCT), SNAPSHOT);
    }

    @Test
    @DisplayName("단정5: 8단계 실행 후 지역 8칩 == 골든 (좌표 편입이 address를 오염시키지 않음)")
    void geocodingDoesNotCorruptAddressRegionChips() {
        ProcessReport report = orchestrator.run(SNAPSHOT);

        // 8단계: 전 59건 좌표 채움(스텁은 항상 성공).
        assertThat(report.geo().geocoded()).isEqualTo(59);
        assertThat(report.geo().skipped()).isZero();
        assertThat(report.geo().failed()).isZero();
        assertThat(breweryRepository.findAll()).allSatisfy(b ->
                assertThat(b.getLatitude()).as("좌표 채움: %s", b.getBreweryId()).isNotNull());

        // address 재파싱 지역 8칩 == 골든(좌표 편입 후에도 불변).
        Map<String, Integer> chip = new TreeMap<>();
        breweryRepository.findAll().forEach(b -> chip.merge(b.getRegion(), 1, Integer::sum));
        assertThat(chip).containsExactlyInAnyOrderEntriesOf(GOLDEN_CHIP);
    }

    @Test
    @DisplayName("단정6: 2회 실행 시 geocoded=0, skipped=59 (좌표 있으면 카카오 미호출)")
    void reRunSkipsGeocoding() {
        orchestrator.run(SNAPSHOT);
        ProcessReport again = orchestrator.run(SNAPSHOT);

        assertThat(again.geo().geocoded()).isZero();
        assertThat(again.geo().skipped()).isEqualTo(59);
        assertThat(again.geo().failed()).isZero();
        assertThat(again.geo().total()).isEqualTo(59);
    }
}
