package com.jeontongjuro.backend.pipeline.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.pipeline.collect.RawCollectService;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import com.jeontongjuro.backend.pipeline.collect.raw.AbstractRawRow;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.RawRowRepository;
import com.jeontongjuro.backend.pipeline.collect.source.FixtureRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * verify 2종: "적재 정합" 검증 (DB 대상 — 로컬 PostgreSQL 필요, docker compose up -d).
 * 골든 픽스처를 collect에 주입해 raw 테이블에 적재한 뒤:
 *   (1) 행수 brewery_raw == 59, product_raw == 1215
 *   (2) source_row_index 0..n-1 연속·유니크·누락 없음
 *   (3) 무손실 스팟체크 — 원본 배열 인덱스 0/중간/마지막 원소의 전 필드 원문값 == DB 값
 *   (4) 같은 snapshot_date 재실행 시 스킵(멱등성)
 *   (5) snapshot_date: 적재된 전 행이 명시 주입한 논리 라벨(2026-07-28)과 일치
 *   (6) collected_at: manifest collected_at 유래 값으로 채워지고 created_at(적재 시각)과 별개
 * 적재 정합을 sha256으로 검증하지 않는다(직렬화 차이로 항상 실패) — 행수+인덱스 연속성+필드 스팟체크로 검증한다.
 * 개발 DB(jeontongjuro) 대신 전용 DB(jeontongjuro_test)를 쓴다(compose init 스크립트가 생성).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 적재 정합 verify 스킵")
class RawLoadConsistencyTest {

    /**
     * 명시 주입하는 논리 스냅샷 라벨(B안) — 픽스처 파일명(20260728_*)과 일치시킨다.
     * 타임존 파생의 우연한 일치에 의존하지 않고 collect()에 직접 주입한다.
     */
    private static final LocalDate EXPECTED_SNAPSHOT_DATE = LocalDate.of(2026, 7, 28);

    @Autowired
    private RawCollectService collectService;
    @Autowired
    private BreweryRawRepository breweryRawRepository;
    @Autowired
    private ProductRawRepository productRawRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private FixtureRawSnapshotSource fixtureSource;

    @BeforeEach
    void loadFixturesIntoRawTables() {
        fixtureSource = new FixtureRawSnapshotSource(objectMapper);
        // 결정적 검증을 위해 전용 테스트 DB의 raw 테이블을 비우고 다시 적재
        breweryRawRepository.deleteAll();
        productRawRepository.deleteAll();
        // snapshot_date 라벨 명시 주입 — 골든 재현은 주입 경로로 고정한다(TZ 파생 없음)
        collectService.collect(fixtureSource.fetch(RawDataset.BREWERY), EXPECTED_SNAPSHOT_DATE);
        collectService.collect(fixtureSource.fetch(RawDataset.PRODUCT), EXPECTED_SNAPSHOT_DATE);
    }

    // DEBT-4: 59·1215(·366) 골든 행수가 이 테스트를 비롯해 GoldenFixtureIntegrityTest·NormalizeGoldenRegressionTest·
    //         ProcessOrchestratorIntegrationTest에 하드코딩돼 있다. 양조장/스냅샷을 추가하면 이 상수들을 한꺼번에 고쳐야 한다. docs/DEBT.md #4
    @Test
    @DisplayName("행수: brewery_raw == 59, product_raw == 1215 (snapshot_date는 주입 라벨 기준)")
    void rowCountsMatchGolden() {
        assertThat(breweryRawRepository.countBySnapshotDate(EXPECTED_SNAPSHOT_DATE)).isEqualTo(59);
        assertThat(productRawRepository.countBySnapshotDate(EXPECTED_SNAPSHOT_DATE)).isEqualTo(1215);
        assertThat(breweryRawRepository.count()).isEqualTo(59);
        assertThat(productRawRepository.count()).isEqualTo(1215);
    }

    @Test
    @DisplayName("source_row_index: 0..n-1 연속·유니크·누락 없음")
    void sourceRowIndexIsContiguous() {
        assertContiguousIndexes(breweryRawRepository, 59);
        assertContiguousIndexes(productRawRepository, 1215);
    }

    private <T extends AbstractRawRow> void assertContiguousIndexes(RawRowRepository<T> repository, int expectedRows) {
        List<Integer> indexes = repository.findAllBySnapshotDateOrderBySourceRowIndexAsc(EXPECTED_SNAPSHOT_DATE)
                .stream().map(AbstractRawRow::getSourceRowIndex).toList();
        // 정렬 조회 결과가 정확히 0,1,...,n-1이면 연속·유니크·누락 없음이 동시에 증명된다
        assertThat(indexes).hasSize(expectedRows);
        for (int i = 0; i < expectedRows; i++) {
            assertThat(indexes.get(i)).as("index %d", i).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("무손실 스팟체크(brewery): 인덱스 0·중간·마지막 원소의 전 필드 원문 == DB 값")
    void breweryFieldsAreLossless() throws IOException {
        JsonNode data = readFixtureData(RawDataset.BREWERY);
        for (int idx : new int[]{0, data.size() / 2, data.size() - 1}) {
            JsonNode row = data.get(idx);
            BreweryRaw entity = breweryRawRepository
                    .findBySnapshotDateAndSourceRowIndex(EXPECTED_SNAPSHOT_DATE, idx).orElseThrow();
            assertText(row, "사용여부", idx, entity.getUseYn());
            assertText(row, "상시방문가능여부", idx, entity.getAnytimeVisitYn());
            assertText(row, "상호명", idx, entity.getBusinessName());
            assertText(row, "예약방문가능여부", idx, entity.getReservationVisitYn());
            assertNumber(row, "조회수", idx, entity.getViewCount());
            assertText(row, "주소", idx, entity.getAddress());
            assertText(row, "홈페이지", idx, entity.getHomepageUrl());
        }
    }

    @Test
    @DisplayName("무손실 스팟체크(product): 인덱스 0·중간·마지막 원소의 전 필드 원문 == DB 값")
    void productFieldsAreLossless() throws IOException {
        JsonNode data = readFixtureData(RawDataset.PRODUCT);
        for (int idx : new int[]{0, data.size() / 2, data.size() - 1}) {
            JsonNode row = data.get(idx);
            ProductRaw entity = productRawRepository
                    .findBySnapshotDateAndSourceRowIndex(EXPECTED_SNAPSHOT_DATE, idx).orElseThrow();
            assertText(row, "성분", idx, entity.getIngredients());
            assertText(row, "수상경력", idx, entity.getAwards());
            assertText(row, "알콜도수", idx, entity.getAlcoholContent());
            assertText(row, "양조장", idx, entity.getBreweryName());
            assertText(row, "양조장주소", idx, entity.getBreweryAddress());
            assertText(row, "용량", idx, entity.getVolume());
            assertText(row, "제품명", idx, entity.getProductName());
            assertText(row, "제품소개", idx, entity.getDescription());
            assertText(row, "특이사항", idx, entity.getSpecialNote());
            assertText(row, "특징", idx, entity.getCharacteristics());
            assertText(row, "판매여부", idx, entity.getSaleYn());
            assertText(row, "홈페이지주소", idx, entity.getHomepageUrl());
        }
    }

    @Test
    @DisplayName("snapshot_date: 적재된 전 행이 명시 주입한 논리 라벨(2026-07-28)과 일치")
    void injectedSnapshotDateLabelIsAppliedToAllRows() {
        assertAllRowsHaveSnapshotLabel(breweryRawRepository, 59);
        assertAllRowsHaveSnapshotLabel(productRawRepository, 1215);
    }

    private <T extends AbstractRawRow> void assertAllRowsHaveSnapshotLabel(RawRowRepository<T> repository,
                                                                           int expectedRows) {
        List<T> all = repository.findAll();
        assertThat(all).hasSize(expectedRows);
        assertThat(all).allSatisfy(row ->
                assertThat(row.getSnapshotDate()).isEqualTo(EXPECTED_SNAPSHOT_DATE));
    }

    @Test
    @DisplayName("collected_at: manifest collected_at(UTC) 유래 값으로 채워지고 created_at(적재 시각)과 별개")
    void collectedAtIsPreservedSeparatelyFromCreatedAt() throws IOException {
        assertCollectedAtSpot(breweryRawRepository, RawDataset.BREWERY);
        assertCollectedAtSpot(productRawRepository, RawDataset.PRODUCT);
    }

    private <T extends AbstractRawRow> void assertCollectedAtSpot(RawRowRepository<T> repository,
                                                                  RawDataset dataset) throws IOException {
        LocalDateTime expected = LocalDateTime.ofInstant(readFixtureCollectedAt(dataset), ZoneOffset.UTC);
        T entity = repository.findBySnapshotDateAndSourceRowIndex(EXPECTED_SNAPSHOT_DATE, 0).orElseThrow();
        assertThat(entity.getCollectedAt())
                .as("%s collected_at == 픽스처 _meta.collected_at(UTC)", dataset).isEqualTo(expected);
        assertThat(entity.getCreatedAt()).as("%s created_at 존재", dataset).isNotNull();
        // 수집 시각(collected_at)과 적재 시각(created_at)은 다른 개념 — 값도 달라야 정상
        assertThat(entity.getCollectedAt())
                .as("%s collected_at ≠ created_at", dataset).isNotEqualTo(entity.getCreatedAt());
    }

    @Test
    @DisplayName("멱등성: 같은 snapshot_date 재실행 시 스킵되고 행수·기존 collected_at 불변")
    void reRunWithSameSnapshotDateIsSkipped() {
        LocalDateTime originalCollectedAt = breweryRawRepository
                .findBySnapshotDateAndSourceRowIndex(EXPECTED_SNAPSHOT_DATE, 0).orElseThrow().getCollectedAt();
        RawCollectService.CollectResult again =
                collectService.collect(fixtureSource.fetch(RawDataset.BREWERY), EXPECTED_SNAPSHOT_DATE);
        assertThat(again.skipped()).isTrue();
        assertThat(again.loadedRows()).isZero();
        assertThat(breweryRawRepository.count()).isEqualTo(59);
        // 스킵 시 기존 적재분 무변경 — 최초 collected_at 보존(append-only 원장)
        assertThat(breweryRawRepository
                .findBySnapshotDateAndSourceRowIndex(EXPECTED_SNAPSHOT_DATE, 0).orElseThrow().getCollectedAt())
                .isEqualTo(originalCollectedAt);
    }

    /** 원본 문자열 필드 비교 — JSON null은 DB null 그대로여야 한다. */
    private void assertText(JsonNode row, String key, int idx, String dbValue) {
        JsonNode value = row.get(key);
        assertThat(value).as("원본 키 존재: %s[%d]", key, idx).isNotNull();
        String expected = value.isNull() ? null : value.asText();
        assertThat(dbValue).as("%s[%d] 원문 일치", key, idx).isEqualTo(expected);
    }

    /** 원본 number 필드(조회수) 비교. */
    private void assertNumber(JsonNode row, String key, int idx, Long dbValue) {
        JsonNode value = row.get(key);
        assertThat(value).as("원본 키 존재: %s[%d]", key, idx).isNotNull();
        Long expected = value.isNull() ? null : value.longValue();
        assertThat(dbValue).as("%s[%d] 원문 일치", key, idx).isEqualTo(expected);
    }

    private JsonNode readFixtureData(RawDataset dataset) throws IOException {
        return readFixture(dataset).get("data");
    }

    /** 픽스처 _meta.collected_at (manifest collected_at과 동일 유래) — collected_at 컬럼 기대값. */
    private Instant readFixtureCollectedAt(RawDataset dataset) throws IOException {
        return Instant.parse(readFixture(dataset).get("_meta").get("collected_at").asText());
    }

    private JsonNode readFixture(RawDataset dataset) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(FixtureRawSnapshotSource.fixturePath(dataset))) {
            assertThat(in).isNotNull();
            return objectMapper.readTree(in);
        }
    }
}
