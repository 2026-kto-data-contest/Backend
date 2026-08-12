package com.jeontongjuro.backend.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.testsupport.ExperienceFixtures;
import com.jeontongjuro.backend.testsupport.StubExperienceApiClient;
import com.jeontongjuro.backend.testsupport.StubExperienceApiConfig;
import com.jeontongjuro.backend.tour.BreweryNearbyRepository;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 체험 롤업(15단계) 삭제형 diff·실패경로 재현(로컬 PostgreSQL 필요). PR #45(특징 태그)가 검증한 것과 같은
 * 4단계 시나리오 + 두 실패 경로를 고정한다:
 * <ol>
 *   <li>적재 → inserted=52</li>
 *   <li>일부 DELETE → 재실행 → 삭제분만 inserted로 복원</li>
 *   <li>유령 행(시드에 없는 조합) 삽입 → 재실행 → deleted 증가</li>
 *   <li>재실행 → inserted=0 deleted=0 updated=0 (멱등)</li>
 * </ol>
 * 추가: payload 변경 → updated 경로, odcloud 실패 → skip(기존 행 보존), 시드 미매칭·키중복 → fail-fast.
 * <p>
 * 라이브 odcloud 대신 {@link StubExperienceApiClient}를 @Primary로 주입해 응답을 제어한다(네트워크 차단).
 */
@SpringBootTest
@Import(StubExperienceApiConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 체험 롤업 verify 스킵")
class ExperienceRollupIntegrationTest {

    /** 시드에 실재하는 양조장명(NAME 매칭 → BRW-002). matched-row 재현용. */
    private static final String SEEDED_NAME = "고도리 와이너리";
    /** 체험 없는 실재 양조장(유령 행 FK 부모로 사용 — 시드 target엔 없음). */
    private static final String NO_EXPERIENCE_BREWERY = "BRW-001";

    @Autowired
    private ExperienceRollupService rollupService;
    @Autowired
    private ExperienceMatchSeedLoadService seedLoadService;
    @Autowired
    private StubExperienceApiClient stubApiClient;
    @Autowired
    private BreweryExperienceRepository experienceRepository;
    @Autowired
    private BreweryMasterLoadService masterLoadService;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ObjectMapper objectMapper;
    // FK 자식 정리(brewery 삭제 전에 비운다)
    @Autowired
    private BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private TourContentRepository tourContentRepository;

    @BeforeEach
    void resetAndSeedBreweries() {
        experienceRepository.deleteAll();      // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();
        masterLoadService.load(goldenBreweryAttributeRows());  // 골든 59 — 시드 30 target FK 부모 전부 존재
        stubApiClient.returning(ExperienceFixtures.rows(objectMapper));  // 기본 52행 복원(테스트 간 오염 방지)
    }

    @Test
    @DisplayName("삭제형 diff 4단계: 적재 52 → 일부삭제 복원 → 유령삭제 → 멱등")
    void deleteAwareDiffFourStages() {
        // ── 1단계: 적재 → inserted=52 ──
        ExperienceRollupService.RollupResult r1 = rollupService.rollup();
        assertThat(r1.skipped()).isFalse();
        assertThat(r1.inserted()).isEqualTo(52);
        assertThat(r1.updated()).isZero();
        assertThat(r1.deleted()).isZero();
        assertThat(r1.unchanged()).isZero();
        assertThat(r1.targetBreweries()).isEqualTo(30);
        assertThat(experienceRepository.count()).isEqualTo(52);

        // ── 2단계: 일부 DELETE → 재실행 → 삭제분만 inserted로 복원 ──
        List<BreweryExperience> some = experienceRepository.findByBreweryIdOrderByProgramNameAsc(
                seededBreweryId());   // 고도리 와이너리(BRW-002)의 체험 전부 삭제
        assertThat(some).isNotEmpty();
        experienceRepository.deleteAll(some);
        long afterDelete = experienceRepository.count();
        assertThat(afterDelete).isEqualTo(52 - some.size());

        ExperienceRollupService.RollupResult r2 = rollupService.rollup();
        assertThat(r2.inserted()).isEqualTo(some.size());   // 삭제분만 복원
        assertThat(r2.deleted()).isZero();
        assertThat(r2.updated()).isZero();
        assertThat(r2.unchanged()).isEqualTo(52 - some.size());
        assertThat(experienceRepository.count()).isEqualTo(52);

        // ── 3단계: 유령 행(시드 target에 없는 조합) 삽입 → 재실행 → deleted 증가 ──
        experienceRepository.save(BreweryExperience.of(
                NO_EXPERIENCE_BREWERY, "유령 프로그램(원본에 없음)", "유령 내용", "유령 장소", "9:99", 999999));
        assertThat(experienceRepository.count()).isEqualTo(53);

        ExperienceRollupService.RollupResult r3 = rollupService.rollup();
        assertThat(r3.deleted()).isEqualTo(1);              // 유령 1건 제거
        assertThat(r3.inserted()).isZero();
        assertThat(r3.updated()).isZero();
        assertThat(r3.unchanged()).isEqualTo(52);
        assertThat(experienceRepository.count()).isEqualTo(52);

        // ── 4단계: 재실행 → 전부 멱등(insert·update·delete 0) ──
        ExperienceRollupService.RollupResult r4 = rollupService.rollup();
        assertThat(r4.inserted()).isZero();
        assertThat(r4.updated()).isZero();
        assertThat(r4.deleted()).isZero();
        assertThat(r4.unchanged()).isEqualTo(52);
        assertThat(experienceRepository.count()).isEqualTo(52);
    }

    @Test
    @DisplayName("payload 변경 → updated 경로(옛 값 갱신, 행수 불변). 특징 태그 삽입전용과 다른 점")
    void changedPayloadTriggersUpdate() {
        rollupService.rollup();  // 52 적재
        // 한 행의 cost·content를 다른 값으로 오염 → 재실행이 목표값으로 되돌린다
        List<BreweryExperience> rows = experienceRepository.findByBreweryIdOrderByProgramNameAsc(seededBreweryId());
        BreweryExperience row = rows.get(0);
        Integer originalCost = row.getCost();
        row.updatePayload("변조된 내용", row.getPlace(), row.getDuration(),
                originalCost == null ? 1 : originalCost + 1);
        experienceRepository.save(row);

        ExperienceRollupService.RollupResult r = rollupService.rollup();
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.inserted()).isZero();
        assertThat(r.deleted()).isZero();
        assertThat(r.unchanged()).isEqualTo(51);
        assertThat(experienceRepository.count()).isEqualTo(52);
        // 값이 목표(원본)로 복원됨
        BreweryExperience restored = experienceRepository.findByBreweryIdOrderByProgramNameAsc(seededBreweryId())
                .get(0);
        assertThat(restored.getContent()).isNotEqualTo("변조된 내용");
        assertThat(restored.getCost()).isEqualTo(originalCost);
    }

    @Test
    @DisplayName("odcloud 호출 실패 → 15단계 skip(기존 행 보존, 예외 전파 안 함)")
    void apiFailureSkipsAndPreservesExistingRows() {
        rollupService.rollup();  // 52 적재
        assertThat(experienceRepository.count()).isEqualTo(52);

        stubApiClient.failing("체험 API 호출 실패(테스트)");
        ExperienceRollupService.RollupResult r = rollupService.rollup();

        assertThat(r.skipped()).isTrue();
        assertThat(r.skipReason()).contains("체험 API 호출 실패");
        assertThat(r.inserted()).isZero();
        assertThat(r.deleted()).isZero();
        assertThat(r.updated()).isZero();
        // 기존 52행 그대로 보존(삭제하지 않는다)
        assertThat(experienceRepository.count()).isEqualTo(52);
    }

    @Test
    @DisplayName("시드 미매칭(시드에 없는 양조장명) → fail-fast(IllegalStateException, 원본 갱신 신호)")
    void seedUnmatchedFailsFast() {
        stubApiClient.returning(List.of(new ExperienceRow(
                "시드에없는양조장", "체험", "내용", "장소", "1:00", 10000)));

        assertThatThrownBy(() -> rollupService.rollup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시드 미매칭");
        // fail-fast는 skip이 아니다 — 예외로 파이프라인을 세운다(사람 확인)
        assertThat(experienceRepository.count()).isZero();
    }

    @Test
    @DisplayName("자연키 중복(같은 brewery_id·program_name 2행) → fail-fast(유일성 위반, 원본 이상)")
    void duplicateNaturalKeyFailsFast() {
        stubApiClient.returning(List.of(
                new ExperienceRow(SEEDED_NAME, "중복프로그램", "내용1", "장소", "1:00", 10000),
                new ExperienceRow(SEEDED_NAME, "중복프로그램", "내용2", "장소", "2:00", 20000)));

        assertThatThrownBy(() -> rollupService.rollup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자연키 중복");
    }

    /** SEEDED_NAME(고도리 와이너리)의 brewery_id를 시드에서 직접 해석(BRW-002). */
    private String seededBreweryId() {
        String id = seedLoadService.breweryIdOf(SEEDED_NAME);
        assertThat(id).as("시드에 '%s' 매핑 존재", SEEDED_NAME).isNotNull();
        return id;
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
