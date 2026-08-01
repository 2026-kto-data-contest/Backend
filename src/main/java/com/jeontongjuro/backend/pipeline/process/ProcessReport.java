package com.jeontongjuro.backend.pipeline.process;

import com.jeontongjuro.backend.brewery.BreweryJoinStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRegionUpdateService;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.product.ProductBreweryJoinService;
import java.time.LocalDate;
import java.util.List;

/**
 * 파생층 오케스트레이션 1회 실행 요약. 각 단계 서비스가 반환한 record를 그대로 담는다(재계산 없음).
 * <p>
 * ★조인은 멱등 skip이지 재동기화가 아니다 — 2회차 실행 시 기존 link는 {@code join.skippedExisting()}으로
 * 유지됨이 드러난다. raw/override를 바꿔 재반영하려면 파생 테이블(product_brewery_link 등)을 수동
 * truncate 후 재실행해야 한다(이번 스코프는 가시화만 — 재계산 모드 미구현).
 */
public record ProcessReport(
        LocalDate snapshotDate,
        long breweryRawCount,
        long productRawCount,
        BreweryMasterLoadService.LoadResult master,
        ManualOverrideSeedLoadService.LoadResult seed,
        ProductBreweryJoinService.JoinResult join,
        BreweryJoinStatusUpdateService.UpdateResult status,
        BreweryRegionUpdateService.UpdateResult region,
        List<StaleOverride> staleOverrides
) {

    /** hit==0 미적중 override 식별 정보(경고 목록용). */
    public record StaleOverride(long overrideId, String matchKeyKind, String matchKey, String breweryId) {
    }

    /** 표준출력용 요약 렌더링(골든과 눈으로 대조 — 서비스 반환 record 값 그대로). */
    public String render(String elapsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("[process 완료 ").append(elapsed).append("]\n");
        sb.append("snapshot ").append(snapshotDate)
                .append(" (brewery_raw=").append(breweryRawCount)
                .append(" product_raw=").append(productRawCount).append(")\n");
        sb.append("brewery loaded=").append(master.loaded())
                .append(" skipped=").append(master.skippedExisting()).append('\n');
        sb.append("override seeded=").append(seed.loaded())
                .append(" skipped=").append(seed.skippedExisting()).append('\n');
        sb.append("join links=").append(join.linked())
                .append(" (AUTO ").append(join.autoLinked())
                .append("·OVERRIDE_NAME ").append(join.overrideNameLinked())
                .append("·OVERRIDE_ROW ").append(join.overrideRowLinked())
                .append(") skippedExisting=").append(join.skippedExisting()).append('\n');
        sb.append("status candidate=").append(status.candidateBreweries())
                .append(" updatedToJoined=").append(status.updatedToJoined())
                .append(" alreadyJoined=").append(status.alreadyJoined()).append('\n');
        sb.append("region total=").append(region.total())
                .append(" changed=").append(region.changed())
                .append(" unchanged=").append(region.unchanged()).append('\n');
        sb.append("⚠ override 미적중: ");
        if (staleOverrides.isEmpty()) {
            sb.append("없음");
        } else {
            sb.append(staleOverrides.size()).append("건");
            for (StaleOverride s : staleOverrides) {
                sb.append("\n    - id=").append(s.overrideId())
                        .append(' ').append(s.matchKeyKind()).append('=').append(s.matchKey())
                        .append(" → ").append(s.breweryId());
            }
        }
        return sb.toString();
    }
}
