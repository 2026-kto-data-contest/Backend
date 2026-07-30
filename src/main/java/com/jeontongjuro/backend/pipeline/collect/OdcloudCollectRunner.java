package com.jeontongjuro.backend.pipeline.collect;

import com.jeontongjuro.backend.pipeline.collect.source.OdcloudRawSnapshotSource;
import com.jeontongjuro.backend.pipeline.collect.source.RawSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 라이브 수집 실행기 — 프로파일 "collect"에서만 동작한다.
 * 실행 예: AT_SERVICE_KEY=... ./gradlew bootRun --args='--spring.profiles.active=collect'
 * (이번 구현 세션에서는 라이브 호출을 수행하지 않았고, 검증은 골든 픽스처 주입으로 대신한다.)
 */
@Component
@Profile("collect")
public class OdcloudCollectRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OdcloudCollectRunner.class);

    private final OdcloudRawSnapshotSource source;
    private final RawCollectService collectService;
    private final CollectProperties properties;

    public OdcloudCollectRunner(OdcloudRawSnapshotSource source, RawCollectService collectService,
                                CollectProperties properties) {
        this.source = source;
        this.collectService = collectService;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        for (RawDataset dataset : RawDataset.values()) {
            RawSnapshot snapshot = source.fetch(dataset);
            // snapshot_date 라벨: --pipeline.collect.snapshot-date=YYYY-MM-DD 주입값(없으면 null → UTC 날짜 폴백)
            RawCollectService.CollectResult result = collectService.collect(snapshot, properties.snapshotDate());
            // serviceKey는 어떤 경우에도 로그에 남기지 않는다.
            if (result.skipped()) {
                log.info("collect {} snapshot_date={} — 이미 적재됨, 스킵", dataset, result.snapshotDate());
            } else {
                log.info("collect {} snapshot_date={} — {}행 적재", dataset, result.snapshotDate(), result.loadedRows());
            }
        }
    }
}
