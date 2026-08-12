package com.jeontongjuro.backend.pipeline.process;

import com.jeontongjuro.backend.brewery.BreweryContentMatchUpdateService;
import com.jeontongjuro.backend.brewery.BreweryCoordinateUpdateService;
import com.jeontongjuro.backend.brewery.BreweryJoinStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryLiquorStatusUpdateService;
import com.jeontongjuro.backend.brewery.BreweryMasterLoadService;
import com.jeontongjuro.backend.brewery.BreweryRegionUpdateService;
import com.jeontongjuro.backend.brewery.KakaoPhoneSeedLoadService;
import com.jeontongjuro.backend.brewery.NonglimSeedLoadService;
import com.jeontongjuro.backend.feature.FeatureRollupService;
import com.jeontongjuro.backend.liquortype.LiquorRollupResult;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.product.ProductBreweryJoinService;
import com.jeontongjuro.backend.tour.TourDetailEnrichService;
import com.jeontongjuro.backend.tour.TourMatchResolveService;
import com.jeontongjuro.backend.tour.TourNearbyCollectService;
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
        LiquorRollupResult liquor,
        BreweryLiquorStatusUpdateService.UpdateResult liquorStatus,
        BreweryCoordinateUpdateService.GeoResult geo,
        TourNearbyCollectService.NearbyResult nearby,
        TourMatchResolveService.ResolveResult matchResolve,
        BreweryContentMatchUpdateService.MatchResult match,
        FeatureRollupService.RollupResult feature,
        TourDetailEnrichService.EnrichResult tourDetail,
        KakaoPhoneSeedLoadService.LoadResult kakaoPhone,
        NonglimSeedLoadService.LoadResult nonglim,
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
        sb.append("alcohol parsed=").append(join.alcoholParsed())
                .append(" failed=").append(join.alcoholFailed())
                .append(" (합=").append(join.alcoholParsed() + join.alcoholFailed())
                .append("==").append(join.linked()).append(")")
                .append(" backfilled=").append(join.alcoholBackfilled())
                .append(" (기존 링크 도수 채움)\n");
        sb.append("status candidate=").append(status.candidateBreweries())
                .append(" updatedToJoined=").append(status.updatedToJoined())
                .append(" alreadyJoined=").append(status.alreadyJoined()).append('\n');
        sb.append("region total=").append(region.total())
                .append(" changed=").append(region.changed())
                .append(" unchanged=").append(region.unchanged()).append('\n');
        sb.append("liquor MANUAL seeded=").append(liquor.manual().loaded())
                .append(" skipped=").append(liquor.manual().skippedExisting())
                .append(" (recheck_flag=false)")
                .append(" | AUTO target=").append(liquor.infer().targetProducts())
                .append(" tagged=").append(liquor.infer().tagRowsInserted())
                .append(" skippedExisting=").append(liquor.infer().skippedExisting())
                .append(" excluded=").append(liquor.infer().excludedTags())
                .append(" suppressed=").append(liquor.infer().suppressedTags())
                .append(" uncovered=").append(liquor.infer().uncoveredProducts())
                .append(" (AUTO는 recheck_flag=true)").append('\n');
        sb.append("liquor_status TAGGED=").append(liquorStatus.tagged())
                .append(" UNTAGGED=").append(liquorStatus.untagged())
                .append(" NA=").append(liquorStatus.na())
                .append(" (changed=").append(liquorStatus.changed())
                .append(" unchanged=").append(liquorStatus.unchanged()).append(")\n");
        sb.append("geo geocoded=").append(geo.geocoded())
                .append(" skipped=").append(geo.skipped())
                .append(" failed=").append(geo.failed())
                .append(" (total=").append(geo.total()).append(")\n");
        sb.append("tour nearby withContent=").append(nearby.withContent())
                .append(" emptyRadius=").append(nearby.emptyRadius())
                .append(" (합=").append(nearby.withContent() + nearby.emptyRadius())
                .append("==").append(nearby.breweries())
                .append(") | contentUpsert(pair-wise, not distinct) ins=").append(nearby.contentUpsertInserted())
                .append(" upd=").append(nearby.contentUpsertUpdated())
                .append(" unch=").append(nearby.contentUpsertUnchanged())
                .append(" noCoord=").append(nearby.contentSkippedNoCoord())
                .append(" | distinctContent=").append(nearby.distinctContentTotal())
                .append(" | nearby ins=").append(nearby.nearbyInserted())
                .append(" unch=").append(nearby.nearbyUnchanged()).append('\n');
        sb.append("tour match seeds=").append(matchResolve.seeds())
                .append(" (cacheCovered=").append(matchResolve.cacheCovered())
                .append("·cachedNotCovered=").append(matchResolve.cachedNotCovered())
                .append("·grounded=").append(matchResolve.grounded()).append(")")
                .append(" | matched=").append(match.matched())
                .append(" unmatched=").append(match.unmatched())
                .append(" (합=").append(match.matched() + match.unmatched())
                .append("==").append(match.breweries()).append(")")
                .append(" changed=").append(match.changed())
                .append(" unchanged=").append(match.unchanged()).append('\n');
        sb.append("feature 롤업 targetBreweries=").append(feature.targetBreweries())
                .append(" inserted=").append(feature.inserted())
                .append(" deleted=").append(feature.deleted())
                .append(" unchanged=").append(feature.unchanged())
                .append(" (삭제형 diff — 유령 없음)\n");
        sb.append("tour-detail 대상=").append(tourDetail.contentBreweries())
                .append(" | overview 백필=").append(tourDetail.overviewBackfilled())
                .append(" skip=").append(tourDetail.overviewSkipped())
                .append(" 없음=").append(tourDetail.overviewMissing())
                .append(" | 상세 채움=").append(tourDetail.tourDetailUpdated())
                .append(" skip=").append(tourDetail.tourDetailSkipped())
                .append(" | 전화TOUR=").append(tourDetail.phoneFromTour()).append('\n');
        sb.append("kakao-phone 시드=").append(kakaoPhone.seedRows())
                .append(" PHONE=").append(kakaoPhone.phoneEntries())
                .append(" 적용=").append(kakaoPhone.applied())
                .append(" TOUR우선skip=").append(kakaoPhone.skippedTourWins())
                .append(" 비PHONE=").append(kakaoPhone.nonPhone())
                .append(" (phone 합=").append(tourDetail.phoneFromTour() + kakaoPhone.applied())
                .append(" = TOUR ").append(tourDetail.phoneFromTour())
                .append(" + KAKAO ").append(kakaoPhone.applied()).append(")\n");
        sb.append("nonglim 시드=").append(nonglim.seedRows())
                .append(" 적용=").append(nonglim.applied())
                .append(" 기존skip=").append(nonglim.skippedExisting())
                .append(" (설립연도·대표자·선정연도·특징 — 소재지·주종·업체명 미포함)\n");
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
