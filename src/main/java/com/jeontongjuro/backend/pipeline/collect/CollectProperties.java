package com.jeontongjuro.backend.pipeline.collect;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * odcloud 수집 설정(application.yml `pipeline.collect`).
 * uddi·dataset_id는 하드코딩하지 않고 여기서만 온다.
 * serviceKey는 환경변수 AT_SERVICE_KEY 로만 주입되며, 키 문자열을 코드·로그·예외 메시지에 절대 싣지 않는다.
 * snapshotDate는 논리 스냅샷 라벨(선택) — 실행 시 --pipeline.collect.snapshot-date=YYYY-MM-DD 로 주입,
 * 미주입(null) 시 collected_at의 UTC 날짜가 라벨이 된다(RawCollectService 참조).
 */
@ConfigurationProperties(prefix = "pipeline.collect")
public record CollectProperties(
        String apiBase,
        String serviceKey,
        LocalDate snapshotDate,
        Dataset brewery,
        Dataset product
) {

    public record Dataset(String datasetId, String uddi, int perPage) {
        /** odcloud 경로 형식(수집 manifest `_meta.path` 실측과 동일): /{dataset_id}/v1/uddi:{uddi} */
        public String path() {
            return "/" + datasetId + "/v1/uddi:" + uddi;
        }
    }

    public Dataset of(RawDataset dataset) {
        return dataset == RawDataset.BREWERY ? brewery : product;
    }
}
