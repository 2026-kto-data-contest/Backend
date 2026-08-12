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
import com.jeontongjuro.backend.liquortype.LiquorInferenceService;
import com.jeontongjuro.backend.liquortype.LiquorManualSeedLoadService;
import com.jeontongjuro.backend.liquortype.LiquorRollupResult;
import com.jeontongjuro.backend.liquortype.LiquorRollupStatusService;
import com.jeontongjuro.backend.override.ManualOverride;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.override.ManualOverrideSeedLoadService;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRawRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.product.ProductBreweryJoinService;
import com.jeontongjuro.backend.tour.TourDetailEnrichService;
import com.jeontongjuro.backend.tour.TourMatchResolveService;
import com.jeontongjuro.backend.tour.TourNearbyCollectService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 파생층 얇은 순서 조정자 — 이미 골든으로 검증된 5개 서비스를 의존 순서대로 호출하고 결과를 요약한다.
 * 비즈니스 로직 0(카운트는 각 서비스 반환 record에서 그대로 온다). 기존 서비스 코드는 손대지 않는다.
 * <p>
 * ★{@code @Transactional}을 얹지 않는다 — 각 서비스 메서드가 스스로 커밋하는 단계별 독립 커밋을 유지한다.
 * 그래서 3단계에서 실패해도 1·2단계 커밋은 남고, 재실행하면 앞 단계는 멱등 skip으로 통과하고 실패 단계부터
 * 재개된다(fail-fast + 멱등 재개).
 * <p>
 * raw 공급: 골든 픽스처를 서비스에 직접 주입하던 테스트와 달리, 운영 경로에서는 지정 snapshot_date의
 * brewery_raw/product_raw를 정렬 조회해 {@code load()}/{@code join()}에 넘긴다.
 */
@Component
public class ProcessOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProcessOrchestrator.class);

    private final BreweryMasterLoadService masterLoadService;
    private final ManualOverrideSeedLoadService overrideSeedLoadService;
    private final ProductBreweryJoinService joinService;
    private final BreweryJoinStatusUpdateService joinStatusUpdateService;
    private final BreweryRegionUpdateService regionUpdateService;
    private final LiquorManualSeedLoadService liquorManualSeedLoadService;
    private final LiquorInferenceService liquorInferenceService;
    private final LiquorRollupStatusService liquorRollupStatusService;
    private final BreweryLiquorStatusUpdateService liquorStatusUpdateService;
    private final BreweryCoordinateUpdateService coordinateUpdateService;
    private final TourNearbyCollectService nearbyCollectService;
    private final TourMatchResolveService matchResolveService;
    private final BreweryContentMatchUpdateService contentMatchUpdateService;
    private final FeatureRollupService featureRollupService;
    private final TourDetailEnrichService tourDetailEnrichService;
    private final KakaoPhoneSeedLoadService kakaoPhoneSeedLoadService;
    private final NonglimSeedLoadService nonglimSeedLoadService;
    private final BreweryRawRepository breweryRawRepository;
    private final ProductRawRepository productRawRepository;
    private final ManualOverrideRepository overrideRepository;

    public ProcessOrchestrator(BreweryMasterLoadService masterLoadService,
                               ManualOverrideSeedLoadService overrideSeedLoadService,
                               ProductBreweryJoinService joinService,
                               BreweryJoinStatusUpdateService joinStatusUpdateService,
                               BreweryRegionUpdateService regionUpdateService,
                               LiquorManualSeedLoadService liquorManualSeedLoadService,
                               LiquorInferenceService liquorInferenceService,
                               LiquorRollupStatusService liquorRollupStatusService,
                               BreweryLiquorStatusUpdateService liquorStatusUpdateService,
                               BreweryCoordinateUpdateService coordinateUpdateService,
                               TourNearbyCollectService nearbyCollectService,
                               TourMatchResolveService matchResolveService,
                               BreweryContentMatchUpdateService contentMatchUpdateService,
                               FeatureRollupService featureRollupService,
                               TourDetailEnrichService tourDetailEnrichService,
                               KakaoPhoneSeedLoadService kakaoPhoneSeedLoadService,
                               NonglimSeedLoadService nonglimSeedLoadService,
                               BreweryRawRepository breweryRawRepository,
                               ProductRawRepository productRawRepository,
                               ManualOverrideRepository overrideRepository) {
        this.masterLoadService = masterLoadService;
        this.overrideSeedLoadService = overrideSeedLoadService;
        this.joinService = joinService;
        this.joinStatusUpdateService = joinStatusUpdateService;
        this.regionUpdateService = regionUpdateService;
        this.liquorManualSeedLoadService = liquorManualSeedLoadService;
        this.liquorInferenceService = liquorInferenceService;
        this.liquorRollupStatusService = liquorRollupStatusService;
        this.liquorStatusUpdateService = liquorStatusUpdateService;
        this.coordinateUpdateService = coordinateUpdateService;
        this.nearbyCollectService = nearbyCollectService;
        this.matchResolveService = matchResolveService;
        this.contentMatchUpdateService = contentMatchUpdateService;
        this.featureRollupService = featureRollupService;
        this.tourDetailEnrichService = tourDetailEnrichService;
        this.kakaoPhoneSeedLoadService = kakaoPhoneSeedLoadService;
        this.nonglimSeedLoadService = nonglimSeedLoadService;
        this.breweryRawRepository = breweryRawRepository;
        this.productRawRepository = productRawRepository;
        this.overrideRepository = overrideRepository;
    }

    /**
     * 지정 snapshot_date의 raw를 근거로 파생층을 순서대로 실행한다.
     *
     * @param snapshotDate 가공 대상 스냅샷 라벨(사람이 명시 — 자동 max 선택 금지)
     */
    public ProcessReport run(LocalDate snapshotDate) {
        if (snapshotDate == null) {
            throw new IllegalArgumentException(
                    "snapshot_date 필수 — 가공 대상 스냅샷을 명시하라. 예: --snapshot=20260728");
        }

        // [1] 정합성·선행조건 가드 — brewery_raw·product_raw가 같은 snapshot_date에 모두 존재해야 진행.
        //     한쪽만 있으면 날짜 엇갈림 조인이므로 반드시 중단(절대수 하드코딩 금지 — 존재 관계만 검사).
        long breweryRawCount = breweryRawRepository.countBySnapshotDate(snapshotDate);
        long productRawCount = productRawRepository.countBySnapshotDate(snapshotDate);
        if (breweryRawCount == 0) {
            throw new IllegalStateException(
                    "이 스냅샷에 brewery_raw 없음 — collect 상태 확인 (snapshot_date=" + snapshotDate + ")");
        }
        if (productRawCount == 0) {
            throw new IllegalStateException(
                    "이 스냅샷에 product_raw 없음 — collect 상태 확인 (snapshot_date=" + snapshotDate + ")");
        }

        // [2] raw 어댑터 — snapshot_date 정렬 조회로 서비스 입력을 공급(골든 픽스처 주입 경로의 운영 대체).
        List<BreweryRaw> breweryRaws =
                breweryRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(snapshotDate);
        List<ProductRaw> productRaws =
                productRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(snapshotDate);

        // [3] 파이프라인 — 각 단계 반환 record 보관, fail-fast(예외 시 어느 단계인지 로그 후 중단).
        BreweryMasterLoadService.LoadResult master =
                step(1, "brewery 마스터 로드", () -> masterLoadService.load(breweryRaws));
        ManualOverrideSeedLoadService.LoadResult seed =
                step(2, "override 시드 적재", overrideSeedLoadService::load);
        ProductBreweryJoinService.JoinResult join =
                step(3, "product→brewery 조인", () -> joinService.join(productRaws));
        // linkedBreweryIds()는 커밋된 link를 다시 읽는 단순 조회(scalar brewery_id) — 트랜잭션 밖 호출 안전.
        Set<String> linkedIds = step(3, "linkedBreweryIds 수집", joinService::linkedBreweryIds);
        BreweryJoinStatusUpdateService.UpdateResult status =
                step(4, "join_status 갱신", () -> joinStatusUpdateService.applyJoinResult(linkedIds));
        BreweryRegionUpdateService.UpdateResult region =
                step(5, "region 갱신", regionUpdateService::apply);
        // 6. liquorRollup — 3d 주종 롤업. MANUAL 시드 선적재(MANUAL wins) → AUTO 추론 순서.
        //    ★AUTO행은 recheck_flag=true(미검증), MANUAL 시드행은 recheck_flag=false(검수 완료).
        LiquorManualSeedLoadService.LoadResult liquorManual =
                step(6, "주종 MANUAL 시드", liquorManualSeedLoadService::load);
        LiquorInferenceService.InferResult liquorInfer =
                step(6, "주종 AUTO 추론", () -> liquorInferenceService.infer(productRaws));
        LiquorRollupResult liquor = new LiquorRollupResult(liquorManual, liquorInfer);
        // 7. liquor_status 전이(이슈 #18 2차) — 4단계 join_status 갱신과 대칭 독립 단계.
        //    조인 제품 전건 태깅 & 전건 recheck_flag=false인 양조장만 TAGGED, 그 외 UNTAGGED,
        //    미조인은 NA. 6단계(태깅) 결과를 읽어 상태 컬럼만 갱신한다(멱등).
        Set<String> confirmedBreweryIds =
                step(7, "확정 양조장 집계", liquorRollupStatusService::confirmedBreweryIds);
        BreweryLiquorStatusUpdateService.UpdateResult liquorStatus =
                step(7, "liquor_status 갱신", () -> liquorStatusUpdateService.applyLiquorRollup(confirmedBreweryIds));
        // 8. 좌표 지오코딩(#28) — 카카오 Local API로 brewery 좌표 편입. 멱등(좌표 있으면 미호출).
        //    ★brewery 마스터(1단계 산출)에 의존하므로 collect가 아니라 process에 편입한다(계층 정합).
        BreweryCoordinateUpdateService.GeoResult geo =
                step(8, "좌표 지오코딩", coordinateUpdateService::apply);
        // 9. 근접 캐싱(TourAPI) — 양조장 반경 20km 콘텐츠를 tour_content·brewery_nearby에 캐시.
        //    대표 좌표는 brewery 좌표(8단계 산물). 멱등·항등식 withContent+emptyRadius==59.
        TourNearbyCollectService.NearbyResult nearby =
                step(9, "근접 캐싱", nearbyCollectService::collect);
        // 10. 매칭 — 확정 시드 20건을 접지(200m 검증, >200m fail-fast)한 뒤 brewery.content_id 대입.
        //     산출(tour: tour_content 접지)과 대입(brewery: content_id) 분리 — 산출 먼저 커밋해 FK 만족.
        TourMatchResolveService.ResolveResult matchResolve =
                step(10, "매칭 산출", matchResolveService::resolve);
        BreweryContentMatchUpdateService.MatchResult match =
                step(10, "매칭 대입", () -> contentMatchUpdateService.apply(matchResolve.confirmed()));
        // 11. 특징 롤업(#43) — 연결 제품의 서술 컬럼에 확정 규칙 적용 → brewery_feature_tag(양조장 grain).
        //     step1 brewery·step3 link에만 의존(4~10과 독립). 삭제형 diff라 규칙 축소·원본 갱신 시 유령 없음.
        FeatureRollupService.RollupResult feature =
                step(11, "특징 롤업", () -> featureRollupService.rollup(productRaws));
        // 12. 관광공사 상세 편입(#50) — content_id 확정 양조장(19)에 detailCommon2/detailIntro2로 소개글·운영시간·
        //     전화(TOUR) 편입. ★10단계(content_id 대입) 이후여야 함(content_id 의존). 기존 행 UPDATE(백필 함정 방어).
        TourDetailEnrichService.EnrichResult tourDetail =
                step(12, "관광공사 상세 편입", tourDetailEnrichService::enrich);
        // 13. 카카오 전화 시드(#50) — TOUR 우선. 12단계가 채운 phone은 두고 빈 곳만 KAKAO로 보충하므로 12단계 뒤.
        KakaoPhoneSeedLoadService.LoadResult kakaoPhone =
                step(13, "카카오 전화 시드", kakaoPhoneSeedLoadService::load);
        // 14. 농림부 지정현황 시드(#50) — 설립연도·대표자·선정연도·특징. 1단계 마스터에만 의존(순서 무관, 말미).
        //     ★소재지·주종·업체명은 시드에 없어 편입 불가(2019 기준일 오염 구조 차단).
        NonglimSeedLoadService.LoadResult nonglim =
                step(14, "농림부 지정현황 시드", nonglimSeedLoadService::load);

        // [4] override 스테일 경고 — hit==0 override는 WARN-and-continue(예외 던지지 않음).
        //     새 uddi 재수집 시 제품명 한 글자 바뀌어 override 스테일 나는 건 정상 시나리오 — 사람이 요약 보고 판단.
        List<ProcessReport.StaleOverride> stale = collectStaleOverrides(join.overrideHitCounts());
        for (ProcessReport.StaleOverride s : stale) {
            log.warn("[process] override 미적중(스테일 후보) id={} {}={} → {}",
                    s.overrideId(), s.matchKeyKind(), s.matchKey(), s.breweryId());
        }

        return new ProcessReport(snapshotDate, breweryRawCount, productRawCount,
                master, seed, join, status, region, liquor, liquorStatus, geo,
                nearby, matchResolve, match, feature, tourDetail, kakaoPhone, nonglim, stale);
    }

    /** overrideHitCounts에 없는(=적중 0건) override를 골라 경고 목록으로. */
    private List<ProcessReport.StaleOverride> collectStaleOverrides(Map<Long, Integer> hitCounts) {
        List<ProcessReport.StaleOverride> stale = new ArrayList<>();
        for (ManualOverride o : overrideRepository.findAll()) {
            if (hitCounts.getOrDefault(o.getId(), 0) == 0) {
                stale.add(new ProcessReport.StaleOverride(
                        o.getId(), o.getMatchKeyKind().name(), o.getMatchKey(), o.getBreweryId()));
            }
        }
        return stale;
    }

    /** 단계 실행 래퍼 — 예외 시 어느 단계인지 로그하고 그대로 전파(fail-fast). */
    private <T> T step(int no, String name, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            log.error("[process] 단계 {} '{}' 실패 — 파이프라인 중단(앞 단계는 독립 커밋됨, 재실행 시 멱등 재개)",
                    no, name);
            throw ex;
        }
    }
}
