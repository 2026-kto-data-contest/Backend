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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 카카오 place 시드 로더(#54, 16단계) 전용 verify. 마스터 로드로 59행을 시드한 뒤
 * {@link KakaoPlaceSeedLoadService#load()}를 돌려:
 *   (1) PLACE 55 전건 적용·NO_MATCH 4 미적용, 카운트 항등식(seedRows 59 = placeEntries 55 + nonPlace 4)
 *   (2) ★기존 phone 값 불변 — 이번 PR 최대 회귀 지점(로더는 kakao_place_url만 건드려야 한다)
 *   (3) 재실행 멱등(2회차 applied=0·unchanged=55, 값 불변)
 *   (4) placeUrl은 http:// 원문 그대로 저장(https 변환 금지)
 * 구조 봉인(키·행수)은 {@link KakaoPlaceSeedFileStructureTest}가 DB 없이 별도로 검증한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — kakao_place 로더 verify 스킵")
class KakaoPlaceSeedLoadServiceTest {

    /** 시드 NO_MATCH 4곳(placeUrl 없음 — 적용 대상 아님). */
    private static final Set<String> NO_MATCH = Set.of("BRW-003", "BRW-027", "BRW-036", "BRW-051");

    @Autowired
    private BreweryMasterLoadService loadService;
    @Autowired
    private KakaoPlaceSeedLoadService placeSeedLoadService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;
    @Autowired
    private com.jeontongjuro.backend.experience.BreweryExperienceRepository experienceRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedMaster() {
        experienceRepository.deleteAll();   // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        loadService.load(goldenBreweryAttributeRows());   // 59행(kakao_place FK 선검증 통과)
    }

    @Test
    @DisplayName("적용: PLACE 55 전건 채움, NO_MATCH 4 미적용, 항등식(59=55+4)·http 원문 유지")
    void appliesPlaceUrlForFiftyFive() {
        KakaoPlaceSeedLoadService.LoadResult result = placeSeedLoadService.load();

        assertThat(result.seedRows()).isEqualTo(59);
        assertThat(result.placeEntries()).isEqualTo(55);
        assertThat(result.applied()).isEqualTo(55);
        assertThat(result.unchanged()).isZero();
        assertThat(result.nonPlace()).isEqualTo(4);
        assertThat(result.placeEntries() + result.nonPlace())
                .as("항등식: PLACE + 비PLACE == 시드 전체").isEqualTo(result.seedRows());

        // DB: kakao_place_url 채워진 행 55
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getKakaoPlaceUrl() != null).count()).isEqualTo(55);
        // NO_MATCH 4곳은 여전히 null
        for (String id : NO_MATCH) {
            assertThat(breweryRepository.findById(id).orElseThrow().getKakaoPlaceUrl())
                    .as("NO_MATCH %s는 url null", id).isNull();
        }
        // http:// 원문 유지(https 변환 금지) — PLACE 행 전건 http:// 접두
        assertThat(breweryRepository.findAll().stream()
                .map(Brewery::getKakaoPlaceUrl)
                .filter(u -> u != null))
                .allSatisfy(u -> assertThat(u).startsWith("http://"));
    }

    @Test
    @DisplayName("★기존 phone 불변: 로더는 kakao_place_url만 건드리고 phone/phone_source는 그대로 둔다")
    void doesNotTouchExistingPhone() {
        // PLACE 대상 양조장 BRW-001에 phone을 미리 심는다(관광공사 TOUR 가정)
        String beforePhone = "041-000-0000";
        Brewery seededPhone = breweryRepository.findById("BRW-001").orElseThrow();
        seededPhone.applyPhone(beforePhone, PhoneSource.TOUR);
        breweryRepository.save(seededPhone);

        placeSeedLoadService.load();

        // phone·phone_source 그대로, kakao_place_url은 새로 채워짐
        Brewery after = breweryRepository.findById("BRW-001").orElseThrow();
        assertThat(after.getPhone()).as("phone 불변").isEqualTo(beforePhone);
        assertThat(after.getPhoneSource()).as("phone_source 불변").isEqualTo(PhoneSource.TOUR);
        assertThat(after.getKakaoPlaceUrl()).as("place url은 채워짐").isNotNull();

        // 로더가 phone을 새로 만들지 않았음: 미리 심은 BRW-001 1곳만 phone 보유
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getPhone() != null).count())
                .as("로더는 phone을 도입하지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("멱등: 재실행 시 applied=0·unchanged=55, 저장값 불변")
    void reRunIsIdempotent() {
        placeSeedLoadService.load();
        String firstUrl = breweryRepository.findById("BRW-001").orElseThrow().getKakaoPlaceUrl();

        KakaoPlaceSeedLoadService.LoadResult again = placeSeedLoadService.load();
        assertThat(again.seedRows()).isEqualTo(59);
        assertThat(again.placeEntries()).isEqualTo(55);
        assertThat(again.applied()).isZero();
        assertThat(again.unchanged()).isEqualTo(55);
        assertThat(again.nonPlace()).isEqualTo(4);

        assertThat(breweryRepository.findById("BRW-001").orElseThrow().getKakaoPlaceUrl())
                .isEqualTo(firstUrl);
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
