package com.jeontongjuro.backend.pipeline.collect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.pipeline.collect.raw.AbstractRawRow;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.RawRowRepository;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAW 적재 서비스 — fetch 결과(RawSnapshot)를 원본 무손실로 brewery_raw/product_raw에 적재한다.
 * 정규화·조인·주종추론·채번은 이 층의 범위 밖이며 수행하지 않는다.
 */
@Service
public class RawCollectService {

    private final BreweryRawRepository breweryRawRepository;
    private final ProductRawRepository productRawRepository;
    private final ObjectMapper rawMapper;

    public RawCollectService(BreweryRawRepository breweryRawRepository,
                             ProductRawRepository productRawRepository,
                             ObjectMapper objectMapper) {
        this.breweryRawRepository = breweryRawRepository;
        this.productRawRepository = productRawRepository;
        // 무손실 보장 장치(FAIL_ON_UNKNOWN_PROPERTIES)는 global.JacksonConfig의 파이프라인 공용 빈에 걸려 있다.
        this.rawMapper = objectMapper;
    }

    public record CollectResult(RawDataset dataset, LocalDate snapshotDate, boolean skipped, int loadedRows) {
    }

    /** 라벨 미주입 수집 — snapshot_date는 collected_at의 UTC 날짜로 결정된다. */
    @Transactional
    public CollectResult collect(RawSnapshot snapshot) {
        return collect(snapshot, null);
    }

    /**
     * snapshot_date 결정 규칙(B안 — 논리 스냅샷 라벨):
     * (a) 명시 주입된 라벨(snapshotDateLabel)이 있으면 그 값,
     * (b) 없으면 collected_at을 UTC로 해석한 날짜.
     * KST 등 로컬 타임존 파생 금지 — 도커/CI(UTC 기본)와 로컬 환경 간 날짜 어긋남을 차단한다.
     * 실제 수집 벽시계는 collected_at 컬럼에 별도 보존한다(snapshot_date와 다른 개념).
     *
     * 멱등성 정책(단순 구현): 같은 snapshot_date가 이미 적재돼 있으면 "전체 스킵"한다(부분 갱신 없음).
     * 스킵 시 기존 적재분은 일절 건드리지 않는다 — 특히 최초 적재의 collected_at을 재실행 시각으로
     * 갱신하지 않는다(append-only 원장, 최초값 보존). 재적재가 필요하면 해당 snapshot_date 행을
     * 수동 삭제 후 다시 실행한다. 스킵 판정이 어긋나 중복 삽입이 시도되더라도
     * UNIQUE(snapshot_date, source_row_index)가 최종 방어선이다.
     */
    @Transactional
    public CollectResult collect(RawSnapshot snapshot, LocalDate snapshotDateLabel) {
        LocalDate snapshotDate = snapshotDateLabel != null
                ? snapshotDateLabel
                : LocalDate.ofInstant(snapshot.collectedAt(), ZoneOffset.UTC);
        return switch (snapshot.dataset()) {
            case BREWERY -> load(snapshot, snapshotDate, BreweryRaw.class, breweryRawRepository);
            case PRODUCT -> load(snapshot, snapshotDate, ProductRaw.class, productRawRepository);
        };
    }

    private <T extends AbstractRawRow> CollectResult load(RawSnapshot snapshot, LocalDate snapshotDate,
                                                          Class<T> entityType, RawRowRepository<T> repository) {
        if (repository.existsBySnapshotDate(snapshotDate)) {
            return new CollectResult(snapshot.dataset(), snapshotDate, true, 0);
        }
        List<T> batch = new ArrayList<>(snapshot.rows().size());
        for (int i = 0; i < snapshot.rows().size(); i++) {
            T row;
            try {
                // 원본 JSON 객체 → 엔티티. 값 변환·정제 없음(한글 키 매핑은 @JsonProperty가 담당).
                row = rawMapper.treeToValue(snapshot.rows().get(i), entityType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(snapshot.dataset() + " row " + i + " 역직렬화 실패 — 원본 키/타입이 매핑 계약(docs/audit/20260729_raw_field_mapping.md)과 다름", e);
            }
            // source_row_index = 원본 전역 순서(리스트 인덱스). 순서 계약은 RawSnapshot 참조.
            // collected_at = 이 스냅샷의 수집 시각(UTC) — snapshot_date(논리 라벨)와 별개로 보존.
            row.assignLoadKey(snapshotDate, i, snapshot.collectedAt());
            batch.add(row);
        }
        repository.saveAll(batch);
        return new CollectResult(snapshot.dataset(), snapshotDate, false, batch.size());
    }
}
