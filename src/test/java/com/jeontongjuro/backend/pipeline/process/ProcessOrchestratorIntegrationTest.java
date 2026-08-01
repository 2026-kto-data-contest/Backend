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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 파생층 오케스트레이터 end-to-end verify(로컬 PostgreSQL 필요). 골든 raw를 collect로 raw 테이블에
 * 적재한 뒤 {@link ProcessOrchestrator#run(LocalDate)}을 돌려 파생 5단계 산출이 골든 회귀 기준선과
 * 일치하는지, 그리고 재실행이 멱등(전부 skip)인지 검증한다. 골든 픽스처 주입 대신 raw 테이블 경로로
 * 서비스가 배선됨을 함께 실증한다.
 */
@SpringBootTest
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetAndSeedRaw() {
        // 파생(FK 자식부터) → raw 순으로 비우고, 골든을 raw 테이블에 적재(운영과 동일한 입력 경로)
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
    @DisplayName("완전 실행: 파생 5단계 산출이 골든 회귀 기준선과 일치")
    void fullRunMatchesGoldenBaseline() {
        ProcessReport report = orchestrator.run(SNAPSHOT);

        // brewery 마스터 59 신규
        assertThat(report.master().ledgerRows()).isEqualTo(59);
        assertThat(report.master().loaded()).isEqualTo(59);
        assertThat(report.master().skippedExisting()).isZero();

        // override 시드 9 신규
        assertThat(report.seed().seedRows()).isEqualTo(9);
        assertThat(report.seed().loaded()).isEqualTo(9);
        assertThat(report.seed().skippedExisting()).isZero();

        // 조인 360 = AUTO 346 · OVERRIDE_NAME 12 · OVERRIDE_ROW 2
        assertThat(report.join().linked()).isEqualTo(360);
        assertThat(report.join().autoLinked()).isEqualTo(346);
        assertThat(report.join().overrideNameLinked()).isEqualTo(12);
        assertThat(report.join().overrideRowLinked()).isEqualTo(2);
        assertThat(report.join().skippedExisting()).isZero();
        assertThat(report.join().autoOverrideConflicts()).isZero();

        // join_status: 후보 58(distinct 연결 brewery) 전부 JOINED로 승격
        assertThat(report.status().candidateBreweries()).isEqualTo(58);
        assertThat(report.status().updatedToJoined()).isEqualTo(58);
        assertThat(report.status().alreadyJoined()).isZero();

        // region: 전 59행 채움
        assertThat(report.region().total()).isEqualTo(59);
        assertThat(report.region().changed()).isEqualTo(59);
        assertThat(report.region().unchanged()).isZero();

        // override 미적중 0(9행 전부 ≥1 적중)
        assertThat(report.staleOverrides()).isEmpty();

        // DB 최종 상태: JOINED 58 · UNJOINED 1(밀과노닐다 BRW-018)
        var all = breweryRepository.findAll();
        assertThat(all.stream().filter(b -> b.getJoinStatus() == JoinStatus.JOINED).count()).isEqualTo(58);
        var unjoined = all.stream().filter(b -> b.getJoinStatus() == JoinStatus.UNJOINED).toList();
        assertThat(unjoined).hasSize(1);
        assertThat(unjoined.get(0).getBreweryId()).isEqualTo("BRW-018");
        assertThat(unjoined.get(0).getBusinessName()).isEqualTo("밀과노닐다");

        // region 8칩 분포 == 골든
        Map<String, Integer> chip = new TreeMap<>();
        breweryRepository.findAll().forEach(b -> chip.merge(b.getRegion(), 1, Integer::sum));
        assertThat(chip).containsExactlyInAnyOrderEntriesOf(GOLDEN_CHIP);
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
        assertThat(again.seed().skippedExisting()).isEqualTo(9);

        // 조인: 신규 link 0, 기존 전량 skip(★재동기화가 아니라 멱등 skip)
        assertThat(again.join().linked()).isZero();
        assertThat(again.join().skippedExisting()).isEqualTo(360);

        // 갱신 단계: 상태 이미 확정 → 신규 승격 0, 이미 JOINED 58 / region 변화 0
        assertThat(again.status().updatedToJoined()).isZero();
        assertThat(again.status().alreadyJoined()).isEqualTo(58);
        assertThat(again.region().changed()).isZero();
        assertThat(again.region().unchanged()).isEqualTo(59);

        // 행수·상태 불변
        assertThat(breweryRepository.count()).isEqualTo(breweryBefore);
        assertThat(linkRepository.count()).isEqualTo(linkBefore);
        assertThat(overrideRepository.count()).isEqualTo(overrideBefore);
        assertThat(breweryRepository.findAll().stream()
                .filter(b -> b.getJoinStatus() == JoinStatus.JOINED).count()).isEqualTo(58);
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
