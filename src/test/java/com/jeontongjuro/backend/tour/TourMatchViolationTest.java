package com.jeontongjuro.backend.tour;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.process.ProcessOrchestrator;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.testsupport.StubGeocodingConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * 10단계 매칭 200m fail-fast(교정2) 검증. 근접 목록은 질의 좌표(근거리)를 주되 detailCommon2는 시드 콘텐츠를
 * 먼 좌표(부산)로 반환한다 — 시드가 캐시에 없어 접지 검증 경로를 타고, 브루어리(서울)와 >200m라 적재하지 않고
 * 정지해야 한다. 사람이 확정한 값이라도 좌표 대조 없이 통과시키지 않음을 실증한다.
 */
@SpringBootTest
@Import({StubGeocodingConfig.class, TourMatchViolationTest.FarDetailStub.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동 — 200m fail-fast verify 스킵")
class TourMatchViolationTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 28);

    /** 근접 목록은 근거리(거리 0), detailCommon2는 시드를 부산(서울 양조장과 >200m)으로 반환. */
    @TestConfiguration
    static class FarDetailStub {
        @Bean
        @Primary
        TourApiClient farStubTourApiClient() {
            return new TourApiClient() {
                @Override
                public List<TourContentRow> locationBasedList(BigDecimal lat, BigDecimal lng, int radiusM) {
                    return List.of(row("STUB-NEARBY-1", lat, lng, 0.0));
                }

                @Override
                public Optional<TourContentRow> detailCommon(String contentId) {
                    // 부산 좌표 — 서울 양조장과 수백 km(>200m) → 접지 검증 실패 유도.
                    return Optional.of(row(contentId,
                            new BigDecimal("35.100000"), new BigDecimal("129.000000"), null));
                }

                @Override
                public Optional<String> detailOverview(String contentId) {
                    // 접지가 200m 실패로 중단되므로 12단계 미도달 — 호출되지 않는다.
                    return Optional.empty();
                }

                @Override
                public Optional<TourIntro> detailIntro(String contentId, String contentTypeId) {
                    return Optional.empty();
                }

                private TourContentRow row(String id, BigDecimal lat, BigDecimal lng, Double dist) {
                    return new TourContentRow(id, "12", "먼콘텐츠", "부산", null, null, null, null,
                            null, null, null, null, null, null, null, null,
                            lng.toPlainString(), lat.toPlainString(), "6", null, null, "Type3",
                            "20200101000000", "20200101000000", dist);
                }
            };
        }
    }

    @Autowired private ProcessOrchestrator orchestrator;
    @Autowired private RawCollectService collectService;
    @Autowired private BreweryRepository breweryRepository;
    @Autowired private ManualOverrideRepository overrideRepository;
    @Autowired private ProductBreweryLinkRepository linkRepository;
    @Autowired private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @Autowired private BreweryRawRepository breweryRawRepository;
    @Autowired private ProductRawRepository productRawRepository;
    @Autowired private BreweryNearbyRepository breweryNearbyRepository;
    @Autowired private TourContentRepository tourContentRepository;
    @Autowired private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.experience.BreweryExperienceRepository experienceRepository;

    @BeforeEach
    void resetAndSeedRaw() {
        experienceRepository.deleteAll();   // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
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
    }

    @Test
    @DisplayName("시드 접지 좌표가 200m 초과면 적재하지 않고 fail-fast로 정지")
    void seedBeyond200mFailsFast() {
        assertThatThrownBy(() -> orchestrator.run(SNAPSHOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("200m 검증 실패");
        // 접지분은 롤백되어 남지 않는다(캐시 STUB-NEARBY-1만 존재, 시드 콘텐츠 접지 0).
        assertThat_noSeedGrounded();
    }

    private void assertThat_noSeedGrounded() {
        // 시드 content_id(745328 등)가 tour_content에 접지되지 않았음 — 매칭 롤백 확인.
        org.assertj.core.api.Assertions.assertThat(tourContentRepository.findById("745328")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getContentId() != null).count()).isZero();
    }
}
