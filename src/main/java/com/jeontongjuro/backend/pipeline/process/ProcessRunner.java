package com.jeontongjuro.backend.pipeline.process;

import com.jeontongjuro.backend.liquortype.LiquorAuditReportService;
import com.jeontongjuro.backend.tour.TourAuditReportService;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 파생층 실행기 — 프로파일 "process"에서만 동작한다. collect의 {@code OdcloudCollectRunner}
 * (CommandLineRunner + {@code @Profile("collect")})와 정확히 대칭. profile 없이 부팅하면 안 돈다.
 * <p>
 * 실행 예: {@code ./gradlew bootRun --args='--spring.profiles.active=process --snapshot=20260728'}
 * <p>
 * snapshot_date는 필수 인자다 — 가공 대상 스냅샷 선택은 사람의 판단 지점이라 자동 max 선택을 하지 않는다.
 */
@Component
@Profile("process")
public class ProcessRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);
    private static final String SNAPSHOT_ARG_PREFIX = "--snapshot=";

    private final ProcessOrchestrator orchestrator;
    private final LiquorAuditReportService liquorAuditReportService;
    private final TourAuditReportService tourAuditReportService;

    public ProcessRunner(ProcessOrchestrator orchestrator,
                         LiquorAuditReportService liquorAuditReportService,
                         TourAuditReportService tourAuditReportService) {
        this.orchestrator = orchestrator;
        this.liquorAuditReportService = liquorAuditReportService;
        this.tourAuditReportService = tourAuditReportService;
    }

    @Override
    public void run(String... args) {
        LocalDate snapshotDate = parseSnapshotArg(args);
        log.info("[process] 시작 snapshot_date={}", snapshotDate);
        long startNanos = System.nanoTime();
        ProcessReport report = orchestrator.run(snapshotDate);
        String elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis() + "ms";
        // 요약은 표준출력으로(골든과 눈으로 대조하는 사람용 리포트 — collect Runner의 로그와 성격이 다름).
        System.out.println(report.render(elapsed));

        // 주종 검수 리포트 3섹션 — 파일(docs/audit/)로 남기고 콘솔에도 출력한다(사람이 이것만 보고 검수).
        String auditReport = liquorAuditReportService.render(snapshotDate);
        Path auditPath = liquorAuditReportService.writeReport(snapshotDate);
        System.out.println();
        System.out.println(auditReport);
        System.out.println("[주종 검수 리포트 기록] " + auditPath);

        // TourAPI 캐싱·매칭 검수 리포트(unmatched 후보 + §9 통계) — 파일·콘솔.
        String tourReport = tourAuditReportService.render();
        Path tourPath = tourAuditReportService.writeReport(snapshotDate);
        System.out.println();
        System.out.println(tourReport);
        System.out.println("[TourAPI 검수 리포트 기록] " + tourPath);
    }

    private LocalDate parseSnapshotArg(String... args) {
        String raw = null;
        for (String arg : args) {
            if (arg != null && arg.startsWith(SNAPSHOT_ARG_PREFIX)) {
                raw = arg.substring(SNAPSHOT_ARG_PREFIX.length()).trim();
            }
        }
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException(
                    "snapshot_date 필수 — 가공 대상 스냅샷을 명시하라. 예: --snapshot=20260728");
        }
        return parseDate(raw);
    }

    /** {@code 20260728}(BASIC_ISO_DATE) 또는 {@code 2026-07-28}(ISO_LOCAL_DATE) 둘 다 허용. */
    private LocalDate parseDate(String raw) {
        try {
            return raw.contains("-")
                    ? LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
                    : LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "snapshot 형식 오류 — YYYYMMDD 또는 YYYY-MM-DD 필요. 입력='" + raw + "'", e);
        }
    }
}
