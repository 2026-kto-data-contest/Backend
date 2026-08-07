package com.jeontongjuro.backend.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.JoinStatus;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import com.jeontongjuro.backend.testsupport.StubGeocodingConfig;
import com.jeontongjuro.backend.testsupport.StubTourApiConfig;
import com.jeontongjuro.backend.tour.BreweryNearbyRepository;
import com.jeontongjuro.backend.tour.TourContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 파생층 오케스트레이터 end-to-end verify(로컬 PostgreSQL 필요). 골든 raw를 collect로 raw 테이블에
 * 적재한 뒤 {@link ProcessOrchestrator#run(LocalDate)}을 돌려 파생 5단계 산출이 골든 회귀 기준선과
 * 일치하는지, 그리고 재실행이 멱등(전부 skip)인지 검증한다. 골든 픽스처 주입 대신 raw 테이블 경로로
 * 서비스가 배선됨을 함께 실증한다.
 */
@SpringBootTest
@Import({StubGeocodingConfig.class, StubTourApiConfig.class})  // 8단계 카카오·9/10단계 TourAPI를 스텁으로 대체(실호출 금지)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — process 오케스트레이터 verify 스킵")
class ProcessOrchestratorIntegrationTest {

    /** 골든 픽스처 파일명(20260728_*)과 일치하는 논리 스냅샷 라벨. */
    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 28);

    /** region 8칩 골든 분포(회귀 기준선). */
    private static final Map<String, Integer> GOLDEN_CHIP = Map.of(
            "수도권", 13, "충청", 17, "전라", 8, "경상", 14, "강원", 3, "제주", 2, "부산", 1, "울산", 1);

    @Autowired
    private ProcessOrchestrator orchestrator;
    @Autowired
    private RawCollectService collectService;
    @Autowired
    private BreweryRawRepository breweryRawRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private TourContentRepository tourContentRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetAndSeedRaw() {
        // 파생(FK 자식부터) → raw 순으로 비우고, 골든을 raw 테이블에 적재(운영과 동일한 입력 경로)
        productLiquorTypeRepository.deleteAll();   // link·brewery FK 자식 — 가장 먼저 비움
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();   // brewery·tour_content FK 자식 — brewery보다 먼저
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();     // 부모 — brewery·nearby 삭제 후 마지막
        breweryRawRepository.deleteAll();
        productRawRepository.deleteAll();
        FixtureRawSnapshotSource source = new FixtureRawSnapshotSource(objectMapper);
        collectService.collect(source.fetch(RawDataset.BREWERY), SNAPSHOT);
        collectService.collect(source.fetch(RawDataset.PRODUCT), SNAPSHOT);
    }

    @Test
    @DisplayName("완전 실행: 파생 5단계 산출이 골든 회귀 기준선과 일치")
    void fullRunMatchesGoldenBaseline() {
        ProcessReport report = orchestrator.run(SNAPSHOT);

        // brewery 마스터 59 신규
        assertThat(report.master().ledgerRows()).isEqualTo(59);
        assertThat(report.master().loaded()).isEqualTo(59);
        assertThat(report.master().skippedExisting()).isZero();

        // override 시드 14 신규
        assertThat(report.seed().seedRows()).isEqualTo(14);
        assertThat(report.seed().loaded()).isEqualTo(14);
        assertThat(report.seed().skippedExisting()).isZero();

        // 조인 366 = AUTO 346 · OVERRIDE_NAME 14 · OVERRIDE_ROW 6
        assertThat(report.join().linked()).isEqualTo(366);
        assertThat(report.join().autoLinked()).isEqualTo(346);
        assertThat(report.join().overrideNameLinked()).isEqualTo(14);
        assertThat(report.join().overrideRowLinked()).isEqualTo(6);
        assertThat(report.join().skippedExisting()).isZero();
        assertThat(report.join().autoOverrideConflicts()).isZero();

        // join_status: 후보 59(distinct 연결 brewery) 전부 JOINED로 승격
        assertThat(report.status().candidateBreweries()).isEqualTo(59);
        assertThat(report.status().updatedToJoined()).isEqualTo(59);
        assertThat(report.status().alreadyJoined()).isZero();

        // region: 전 59행 채움
        assertThat(report.region().total()).isEqualTo(59);
        assertThat(report.region().changed()).isEqualTo(59);
        assertThat(report.region().unchanged()).isZero();

        // override 미적중 0(14행 전부 ≥1 적중)
        assertThat(report.staleOverrides()).isEmpty();

        // DB 최종 상태: JOINED 59 전건 · UNJOINED 0(밀과노닐다 BRW-018 조인 복구 확인)
        var all = breweryRepository.findAll();
        assertThat(all.stream().filter(b -> b.getJoinStatus() == JoinStatus.JOINED).count()).isEqualTo(59);
        var unjoined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.UNJOINED).toList();
        assertThat(unjoined).isEmpty();

        // region 8칩 분포 == 골든
        Map<String, Integer> chip = new TreeMap<>();
        breweryRepository.findAll().forEach(b -> chip.merge(b.getRegion(), 1, Integer::sum));
        assertThat(chip).containsExactlyInAnyOrderEntriesOf(GOLDEN_CHIP);

        // 9단계 근접 캐싱: 스텁이 전 양조장에 콘텐츠 1건 → withContent=59, emptyRadius=0 (항등식)
        assertThat(report.nearby().breweries()).isEqualTo(59);
        assertThat(report.nearby().withContent()).isEqualTo(59);
        assertThat(report.nearby().emptyRadius()).isZero();
        assertThat(report.nearby().withContent() + report.nearby().emptyRadius()).isEqualTo(59);

        // 10단계 매칭: 확정 시드 19건 전부 캐시 미커버(스텁 콘텐츠 id 상이) → detailCommon 접지 19건 확정
        //   (BRW-006은 실기동 665m 검증 실패로 시드에서 제거 — matched=19)
        assertThat(report.matchResolve().seeds()).isEqualTo(19);
        assertThat(report.matchResolve().grounded()).isEqualTo(19);
        assertThat(report.match().matched()).isEqualTo(19);
        assertThat(report.match().unmatched()).isEqualTo(40);
        assertThat(report.match().matched() + report.match().unmatched()).isEqualTo(59);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getContentId() != null).count()).isEqualTo(19);
    }

    /** 3차 MANUAL 시드로 조인 제품이 전건 확정된 양조장 12곳(→ TAGGED 전이 예상). */
    private static final java.util.Set<String> SEEDED_BREWERIES = java.util.Set.of(
            "BRW-007", "BRW-009", "BRW-010", "BRW-014", "BRW-016", "BRW-019",
            "BRW-025", "BRW-033", "BRW-034", "BRW-041", "BRW-043", "BRW-054");

    @Test
    @DisplayName("주종 롤업(6·7단계): MANUAL 221 적재(74+3B 147) → 확정 양조장만 TAGGED 전이, 나머지 UNTAGGED, '기타' 미생성")
    void liquorRollupTransitionsStatusForConfirmedBreweries() {
        ProcessReport report = orchestrator.run(SNAPSHOT);

        // 6단계: MANUAL 시드 221(74+3B 147) 적재 + AUTO 추론 태깅
        assertThat(report.liquor().manual().loaded()).isEqualTo(221);
        assertThat(report.liquor().infer().tagRowsInserted()).isGreaterThan(0);

        // AUTO는 '기타'를 만들지 않는다(MANUAL만 '기타' 부여 가능 — 이번 시드엔 없음)
        assertThat(productLiquorTypeRepository.findAll())
                .noneSatisfy(t -> assertThat(t.getLiquorType())
                        .isEqualTo(com.jeontongjuro.backend.liquortype.LiquorType.기타));

        // 7단계 전이: 시드 12곳 TAGGED, 그 외 59-12=47곳 UNTAGGED, NA 0(전건 JOINED — 2축 유지)
        assertThat(report.liquorStatus().tagged()).isEqualTo(12);
        assertThat(report.liquorStatus().untagged()).isEqualTo(47);
        assertThat(report.liquorStatus().na()).isZero();
        breweryRepository.findAll().forEach(b -> {
            var expected = SEEDED_BREWERIES.contains(b.getBreweryId())
                    ? com.jeontongjuro.backend.brewery.LiquorStatus.TAGGED
                    : com.jeontongjuro.backend.brewery.LiquorStatus.UNTAGGED;
            assertThat(b.getLiquorStatus()).as("liquor_status: %s", b.getBreweryId()).isEqualTo(expected);
        });

        // 2축 정합: JOINED인데 NA인 행 0
        assertThat(breweryRepository.findAll())
                .filteredOn(b -> b.getJoinStatus() == JoinStatus.JOINED)
                .noneSatisfy(b -> assertThat(b.getLiquorStatus())
                        .isEqualTo(com.jeontongjuro.backend.brewery.LiquorStatus.NA));
    }

    @Test
    @DisplayName("멱등 재실행: 2회차는 전부 skip, 파생 행수·상태 불변")
    void reRunIsIdempotent() {
        orchestrator.run(SNAPSHOT);
        long breweryBefore = breweryRepository.count();
        long linkBefore = linkRepository.count();
        long overrideBefore = overrideRepository.count();

        ProcessReport again = orchestrator.run(SNAPSHOT);

        // 적재 단계: 신규 0, 기존 전량 skip
        assertThat(again.master().loaded()).isZero();
        assertThat(again.master().skippedExisting()).isEqualTo(59);
        assertThat(again.seed().loaded()).isZero();
        assertThat(again.seed().skippedExisting()).isEqualTo(14);

        // 조인: 신규 link 0, 기존 전량 skip(★재동기화가 아니라 멱등 skip)
        assertThat(again.join().linked()).isZero();
        assertThat(again.join().skippedExisting()).isEqualTo(366);

        // 갱신 단계: 상태 이미 확정 → 신규 승격 0, 이미 JOINED 59 / region 변화 0
        assertThat(again.status().updatedToJoined()).isZero();
        assertThat(again.status().alreadyJoined()).isEqualTo(59);
        assertThat(again.region().changed()).isZero();
        assertThat(again.region().unchanged()).isEqualTo(59);

        // 6단계 주종 롤업: MANUAL 시드 221(3차) 전건 skip(멱등), 7단계 liquor_status 변화 0
        assertThat(again.liquor().manual().loaded()).isZero();
        assertThat(again.liquor().manual().skippedExisting()).isEqualTo(221);
        assertThat(again.liquorStatus().changed()).isZero();
        assertThat(again.liquorStatus().unchanged()).isEqualTo(59);

        // 행수·상태 불변
        assertThat(breweryRepository.count()).isEqualTo(breweryBefore);
        assertThat(linkRepository.count()).isEqualTo(linkBefore);
        assertThat(overrideRepository.count()).isEqualTo(overrideBefore);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getJoinStatus() == JoinStatus.JOINED).count()).isEqualTo(59);

        // 9·10단계 멱등: 근접캐시 신규 0(전량 unchanged), 매칭 변화 0(전량 unchanged), 접지 재적재 0
        assertThat(again.nearby().withContent()).isEqualTo(59);
        assertThat(again.nearby().contentUpsertInserted()).isZero();
        assertThat(again.nearby().nearbyInserted()).isZero();
        assertThat(again.matchResolve().grounded()).isZero();
        assertThat(again.match().matched()).isEqualTo(19);
        assertThat(again.match().changed()).isZero();
        assertThat(again.match().unchanged()).isEqualTo(19);
    }

    @Test
    @DisplayName("가드: 존재하지 않는 snapshot_date는 즉시 중단(brewery_raw 없음)")
    void missingSnapshotThrows() {
        assertThatThrownBy(() -> orchestrator.run(LocalDate.of(2099, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("brewery_raw 없음");
    }

    @Test
    @DisplayName("가드: 한쪽 raw만 있으면(product_raw 결측) 날짜 엇갈림으로 중단")
    void oneSidedSnapshotThrows() {
        // 같은 snapshot의 product_raw만 제거 → brewery_raw만 존재하는 반쪽 상태
        productRawRepository.deleteAll();
        assertThatThrownBy(() -> orchestrator.run(SNAPSHOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("product_raw 없음");
    }

    @Test
    @DisplayName("가드: snapshot_date null은 즉시 중단")
    void nullSnapshotThrows() {
        assertThatThrownBy(() -> orchestrator.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot_date 필수");
    }
}
