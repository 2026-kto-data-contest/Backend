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
import com.jeontongjuro.backend.experience.BreweryExperienceRepository;
import com.jeontongjuro.backend.testsupport.StubExperienceApiConfig;
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
@Import({StubGeocodingConfig.class, StubTourApiConfig.class, StubExperienceApiConfig.class})  // 8·9/10·15단계 외부 API 스텁 대체(실호출 금지)
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
    @org.springframework.beans.factory.annotation.Autowired
    private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private TourContentRepository tourContentRepository;
    @Autowired
    private BreweryExperienceRepository experienceRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void resetAndSeedRaw() {
        // 파생(FK 자식부터) → raw 순으로 비우고, 골든을 raw 테이블에 적재(운영과 동일한 입력 경로)
        experienceRepository.deleteAll();          // brewery FK 자식(#52) — brewery보다 먼저
        featureTagRepository.deleteAll();          // brewery FK 자식(#43) — brewery보다 먼저
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

        // 도수 파싱(#35): 신규 적재 366 전건 파싱 성공·실패 0, 항등식 parsed+failed==linked
        assertThat(report.join().alcoholParsed()).isEqualTo(366);
        assertThat(report.join().alcoholFailed()).isZero();
        assertThat(report.join().alcoholParsed() + report.join().alcoholFailed())
                .isEqualTo(report.join().linked());
        // 전건 신규 적재 경로이므로 백필은 0 (백필은 기존 링크 대상 — 별도 재현 테스트는 consistency 참조)
        assertThat(report.join().alcoholBackfilled()).isZero();

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

        // 12단계 관광공사 상세: content_id 19곳 전부 소개글·상세 백필(스텁이 전건 반환), 전화 TOUR 19
        assertThat(report.tourDetail().contentBreweries()).isEqualTo(19);
        assertThat(report.tourDetail().overviewBackfilled()).isEqualTo(19);
        assertThat(report.tourDetail().overviewSkipped()).isZero();
        assertThat(report.tourDetail().tourDetailUpdated()).isEqualTo(19);
        assertThat(report.tourDetail().phoneFromTour()).isEqualTo(19);
        // overview 백필: 기존 tour_content 행에 UPDATE(신규 insert 아님) — 채워진 행 19
        assertThat(tourContentRepository.findAll().stream()
                .filter(tc -> tc.getOverview() != null).count()).isEqualTo(19);

        // 13단계 카카오 전화: TOUR 19곳(그중 카카오 PHONE 18은 TOUR우선 skip) → 카카오 신규 적용 33, 합 52
        assertThat(report.kakaoPhone().seedRows()).isEqualTo(59);
        assertThat(report.kakaoPhone().phoneEntries()).isEqualTo(51);
        assertThat(report.kakaoPhone().applied()).isEqualTo(33);
        assertThat(report.kakaoPhone().skippedTourWins()).isEqualTo(18);
        assertThat(report.tourDetail().phoneFromTour() + report.kakaoPhone().applied()).isEqualTo(52);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getPhone() != null).count()).isEqualTo(52);
        // phone_source 분포: TOUR 19 / KAKAO 33
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getPhoneSource() == com.jeontongjuro.backend.brewery.PhoneSource.TOUR).count())
                .isEqualTo(19);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getPhoneSource() == com.jeontongjuro.backend.brewery.PhoneSource.KAKAO).count())
                .isEqualTo(33);

        // 14단계 농림부: 36곳 적용(설립연도). 소재지·주종·업체명 컬럼 자체가 없어 오염 구조 차단
        assertThat(report.nonglim().seedRows()).isEqualTo(36);
        assertThat(report.nonglim().applied()).isEqualTo(36);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getFoundedYear() != null).count()).isEqualTo(36);

        // 15단계 체험(#52): 스텁 픽스처 52행 전건 신규 삽입, 30 양조장에 매핑, 갱신·삭제 0. skip 아님.
        assertThat(report.experience().skipped()).isFalse();
        assertThat(report.experience().apiRows()).isEqualTo(52);
        assertThat(report.experience().targetBreweries()).isEqualTo(30);
        assertThat(report.experience().inserted()).isEqualTo(52);
        assertThat(report.experience().updated()).isZero();
        assertThat(report.experience().deleted()).isZero();
        assertThat(report.experience().unchanged()).isZero();
        assertThat(experienceRepository.count()).isEqualTo(52);

        // 16단계 카카오 place URL(#54): 시드 59행 중 PLACE 55 전건 신규 적용, NO_MATCH 4는 비적용.
        //   ★applied만 보면 NO_MATCH가 잘못 적용돼도 55가 나올 수 있으므로 seedRows·nonPlace도 함께 건다.
        assertThat(report.kakaoPlace().seedRows()).isEqualTo(59);
        assertThat(report.kakaoPlace().placeEntries()).isEqualTo(55);
        assertThat(report.kakaoPlace().applied()).isEqualTo(55);
        assertThat(report.kakaoPlace().unchanged()).isZero();
        assertThat(report.kakaoPlace().nonPlace()).isEqualTo(4);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getKakaoPlaceUrl() != null).count()).isEqualTo(55);
    }

    @Test
    @DisplayName("백필 재현(#50, #36 사고 방지): 기존 행 컬럼 null화 후 재실행 → 전용 UPDATE 단계가 다시 채운다")
    void backfillsExistingRowsNotOnlyFreshInserts() {
        // 1) 완주 — 기존 행(brewery 59·tour_content)이 이미 존재하는 상태를 만든다(★fresh insert 아님)
        orchestrator.run(SNAPSHOT);
        // 사전 조건: 채워져 있음
        assertThat(countBrewery("operating_hours IS NOT NULL")).isEqualTo(19);
        assertThat(countBrewery("phone IS NOT NULL")).isEqualTo(52);
        assertThat(countBrewery("founded_year IS NOT NULL")).isEqualTo(36);
        assertThat(countBrewery("kakao_place_url IS NOT NULL")).isEqualTo(55);
        assertThat(countTourContent("overview IS NOT NULL")).isEqualTo(19);

        // 2) 기존 행의 신규 컬럼만 null로 되돌린다 — 행은 그대로 남는다(도수 #36이 놓친 바로 그 경로)
        jdbc.execute("UPDATE brewery SET operating_hours=NULL, rest_date=NULL, parking_info=NULL, "
                + "accom_count=NULL, phone=NULL, phone_source=NULL, founded_year=NULL, "
                + "representative_name=NULL, designated_year=NULL, designation_note=NULL, kakao_place_url=NULL");
        jdbc.execute("UPDATE tour_content SET overview=NULL, overview_fetched_at=NULL");
        assertThat(countBrewery("phone IS NOT NULL")).isZero();
        assertThat(countBrewery("kakao_place_url IS NOT NULL")).isZero();
        assertThat(countTourContent("overview IS NOT NULL")).isZero();
        long breweryRowsBefore = breweryRepository.count();
        long tourRowsBefore = tourContentRepository.count();

        // 3) 재실행 — 1~11단계는 멱등 skip(신규 insert 0), 12·13·14 전용 UPDATE가 기존 행을 다시 채운다
        ProcessReport again = orchestrator.run(SNAPSHOT);
        assertThat(again.master().loaded()).isZero();               // 마스터는 기존 skip(=insert 경로 아님을 확증)
        assertThat(again.match().changed()).isZero();               // 매칭도 기존 유지
        assertThat(again.tourDetail().overviewBackfilled()).isEqualTo(19);
        assertThat(again.tourDetail().tourDetailUpdated()).isEqualTo(19);
        assertThat(again.kakaoPhone().applied()).isEqualTo(33);
        assertThat(again.nonglim().applied()).isEqualTo(36);
        assertThat(again.kakaoPlace().applied()).isEqualTo(55);  // null화된 기존 행을 16단계가 다시 채움

        // 행수 불변(UPDATE지 insert 아님) + 값 재충전 확인
        assertThat(breweryRepository.count()).isEqualTo(breweryRowsBefore);
        assertThat(tourContentRepository.count()).isEqualTo(tourRowsBefore);
        assertThat(countBrewery("operating_hours IS NOT NULL")).isEqualTo(19);
        assertThat(countBrewery("phone IS NOT NULL")).isEqualTo(52);
        assertThat(countBrewery("founded_year IS NOT NULL")).isEqualTo(36);
        assertThat(countBrewery("kakao_place_url IS NOT NULL")).isEqualTo(55);
        assertThat(countTourContent("overview IS NOT NULL")).isEqualTo(19);
    }

    private long countBrewery(String whereClause) {
        return jdbc.queryForObject("SELECT count(*) FROM brewery WHERE " + whereClause, Long.class);
    }

    private long countTourContent(String whereClause) {
        return jdbc.queryForObject("SELECT count(*) FROM tour_content WHERE " + whereClause, Long.class);
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

        // 12·13·14단계 멱등: 이미 채워진 상태라 전부 skip(신규 0)
        assertThat(again.tourDetail().overviewBackfilled()).isZero();
        assertThat(again.tourDetail().overviewSkipped()).isEqualTo(19);
        assertThat(again.tourDetail().tourDetailUpdated()).isZero();
        assertThat(again.tourDetail().tourDetailSkipped()).isEqualTo(19);
        assertThat(again.kakaoPhone().applied()).isZero();
        assertThat(again.kakaoPhone().skippedTourWins()).isEqualTo(51);
        assertThat(again.nonglim().applied()).isZero();
        assertThat(again.nonglim().skippedExisting()).isEqualTo(36);

        // 15단계 체험 멱등: 목표·현재 동일 → 삽입·갱신·삭제 0, 전건 unchanged, 행수 불변
        assertThat(again.experience().skipped()).isFalse();
        assertThat(again.experience().inserted()).isZero();
        assertThat(again.experience().updated()).isZero();
        assertThat(again.experience().deleted()).isZero();
        assertThat(again.experience().unchanged()).isEqualTo(52);
        assertThat(experienceRepository.count()).isEqualTo(52);

        // 16단계 카카오 place URL 멱등: 이미 같은 URL이라 신규 적용 0, PLACE 55 전건 unchanged
        assertThat(again.kakaoPlace().seedRows()).isEqualTo(59);
        assertThat(again.kakaoPlace().applied()).isZero();
        assertThat(again.kakaoPlace().unchanged()).isEqualTo(55);
        assertThat(again.kakaoPlace().nonPlace()).isEqualTo(4);
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
