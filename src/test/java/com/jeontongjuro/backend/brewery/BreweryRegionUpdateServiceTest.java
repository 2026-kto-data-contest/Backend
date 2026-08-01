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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * sido/region UPDATE 정합 verify(3d, 주소 파싱 적용). 마스터 로드(sido/region null)로 59행을 시드한 뒤
 * {@link BreweryRegionUpdateService#apply()}를 돌려:
 *   (1) 전 행 sido·region non-null(파싱 성공률 100%)
 *   (2) region 8칩 분포 == 골든
 *   (3) 재실행 멱등(2회차 changed=0·unchanged=59, 값 불변)
 * 순수 파서 골든 대조는 {@link BreweryRegionParserGoldenTest}가 별도로 검증한다 — 여기선 DB 적용·멱등만 본다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — region UPDATE verify 스킵")
class BreweryRegionUpdateServiceTest {

    private static final Map<String, Integer> GOLDEN_CHIP = Map.of(
            "수도권", 13, "충청", 17, "전라", 8, "경상", 14, "강원", 3, "제주", 2, "부산", 1, "울산", 1);

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedMaster() {
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());
    }

    @Test
    @DisplayName("적용: 전 행 sido·region non-null, 8칩 분포 == 골든")
    void fillsAllRowsMatchingGoldenChipDistribution() {
        BreweryRegionUpdateService.UpdateResult result = regionUpdateService.apply();

        assertThat(result.total()).isEqualTo(59);
        assertThat(result.changed()).isEqualTo(59);
        assertThat(result.unchanged()).isZero();

        Map<String, Integer> chip = new TreeMap<>();
        assertThat(breweryRepository.findAll()).allSatisfy(b -> {
            assertThat(b.getSido()).as("%s sido non-null", b.getBusinessName()).isNotBlank();
            assertThat(b.getRegion()).as("%s region non-null", b.getBusinessName()).isNotBlank();
        });
        breweryRepository.findAll().forEach(b -> chip.merge(b.getRegion(), 1, Integer::sum));
        assertThat(chip).containsExactlyInAnyOrderEntriesOf(GOLDEN_CHIP);
    }

    @Test
    @DisplayName("멱등: 재실행 시 changed=0·unchanged=59, 값 불변")
    void reapplyIsIdempotent() {
        regionUpdateService.apply();
        Map<String, String> firstSido = new TreeMap<>();
        breweryRepository.findAll().forEach(b -> firstSido.put(b.getBreweryId(), b.getSido()));

        BreweryRegionUpdateService.UpdateResult again = regionUpdateService.apply();
        assertThat(again.total()).isEqualTo(59);
        assertThat(again.changed()).isZero();
        assertThat(again.unchanged()).isEqualTo(59);

        breweryRepository.findAll()
                .forEach(b -> assertThat(b.getSido()).isEqualTo(firstSido.get(b.getBreweryId())));
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
