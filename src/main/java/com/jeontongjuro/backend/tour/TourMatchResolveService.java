package com.jeontongjuro.backend.tour;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10단계 매칭 산출(tour 계층). 확정 시드 20건을 tour_content에 접지(FK 근거 확보)하고 전제 12(200m 이내)를
 * 검증한다. 결과는 {@code brewery_id → content_id} 확정 맵으로, 대입은
 * {@code BreweryContentMatchUpdateService}(brewery 계층)가 한다.
 * <p>
 * ★매칭의 1차 목적은 "캐시에서 자기 자신 제외"지만, 캐시에 없는 콘텐츠는 제외할 대상이 애초에 없다.
 * 따라서 미커버 시드를 detailCommon2로 접지하는 이유는 <b>제외가 아니라 content_id·대표사진 확보</b>다
 * (자기 제외는 별도로 {@code brewery_nearby.content_id = brewery.content_id} 파생이 담당).
 * <p>
 * 전제 12 검증(교정2):
 * <ul>
 *   <li>캐시 커버분(brewery_nearby 쌍 존재) → {@code distance_m} 직접 사용.</li>
 *   <li>캐시에 콘텐츠는 있으나 이 양조장 반경엔 없음 → tour_content 좌표로 Haversine.</li>
 *   <li>미커버분 → detailCommon2 응답 좌표로 Haversine, ≤200m면 접지 적재.</li>
 * </ul>
 * ★200m 초과 시드가 하나라도 있으면 적재하지 않고 전량 보고 후 정지한다(fail-fast — @Transactional 롤백으로
 * 접지분도 되돌아간다). 사람이 확정한 값이라도 좌표 대조 없이 통과시키지 않는다.
 */
@Service
public class TourMatchResolveService {

    private static final Logger log = LoggerFactory.getLogger(TourMatchResolveService.class);
    private static final double MATCH_RADIUS_M = 200.0;

    private final TourMatchSeedLoadService seedLoader;
    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;
    private final BreweryNearbyRepository nearbyRepository;
    private final TourApiClient client;

    public TourMatchResolveService(TourMatchSeedLoadService seedLoader,
                                   BreweryRepository breweryRepository,
                                   TourContentRepository tourContentRepository,
                                   BreweryNearbyRepository nearbyRepository,
                                   TourApiClient client) {
        this.seedLoader = seedLoader;
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
        this.nearbyRepository = nearbyRepository;
        this.client = client;
    }

    /** 시드 1건의 200m 검증 상세(로그·리포트용). basis: CACHE_NEARBY / TC_COORD / DETAIL_GROUND. */
    public record SeedDistance(String breweryId, String contentId, double distanceM, String basis) {
    }

    /**
     * @param seeds            처리한 시드 수
     * @param cacheCovered     이 양조장 반경 캐시에 콘텐츠가 있어 distance_m으로 검증한 수
     * @param cachedNotCovered 콘텐츠는 캐시에 있으나 이 양조장 반경엔 없어 tour_content 좌표로 검증한 수
     * @param grounded         캐시 미커버라 detailCommon2로 접지 적재한 수(≤200m)
     * @param confirmed        200m 검증을 통과한 확정 매칭 (brewery_id → content_id) — 대입 입력
     * @param distances        전 시드 검증 상세(≤200m만 도달; >200m면 아래 예외로 정지)
     */
    public record ResolveResult(int seeds, int cacheCovered, int cachedNotCovered, int grounded,
                                Map<String, String> confirmed, List<SeedDistance> distances) {
    }

    @Transactional
    public ResolveResult resolve() {
        Map<String, String> confirmed = new LinkedHashMap<>();
        List<SeedDistance> distances = new ArrayList<>();
        List<SeedDistance> violations = new ArrayList<>();
        int cacheCovered = 0;
        int cachedNotCovered = 0;
        int grounded = 0;

        for (TourMatchSeedLoadService.SeedEntry seed : seedLoader.entries()) {
            String breweryId = seed.breweryId();
            String contentId = seed.contentId();
            Brewery b = breweryRepository.findById(breweryId).orElseThrow(() ->
                    new IllegalStateException("시드가 미지 양조장 참조 brewery_id=" + breweryId));
            if (b.getLatitude() == null || b.getLongitude() == null) {
                throw new IllegalStateException("시드 검증 불가 — 좌표 없음 brewery_id=" + breweryId + "(8단계 확인)");
            }

            double distanceM;
            String basis;
            Optional<TourContent> tc = tourContentRepository.findById(contentId);
            if (tc.isPresent()) {
                Optional<BreweryNearby> pair =
                        nearbyRepository.findById(new BreweryNearbyId(breweryId, contentId));
                if (pair.isPresent() && pair.get().getDistanceM() != null) {
                    distanceM = pair.get().getDistanceM().doubleValue();
                    basis = "CACHE_NEARBY";
                    cacheCovered++;
                } else {
                    if (tc.get().getLatitude() == null || tc.get().getLongitude() == null) {
                        throw new IllegalStateException(
                                "시드 콘텐츠 좌표 없음(검증 불가) content_id=" + contentId + " brewery_id=" + breweryId);
                    }
                    distanceM = TourGeoValidator.haversineMeters(
                            b.getLatitude(), b.getLongitude(),
                            tc.get().getLatitude(), tc.get().getLongitude());
                    basis = "TC_COORD";
                    cachedNotCovered++;
                }
            } else {
                // 캐시 미커버 → detailCommon2 접지(content_id·사진 확보). 좌표로 200m 검증.
                Optional<TourContentRow> detail = client.detailCommon(contentId);
                if (detail.isEmpty()) {
                    throw new IllegalStateException(
                            "시드 콘텐츠 detailCommon2 결과 없음(접지 불가) content_id=" + contentId + " brewery_id=" + breweryId);
                }
                TourContentRow row = detail.get();
                Optional<TourGeoValidator.LatLng> coord =
                        TourGeoValidator.validate(contentId, row.mapx(), row.mapy());
                if (coord.isEmpty()) {
                    throw new IllegalStateException(
                            "시드 콘텐츠 좌표 파싱 불가(접지 불가) content_id=" + contentId);
                }
                TourGeoValidator.LatLng ll = coord.get();
                distanceM = TourGeoValidator.haversineMeters(
                        b.getLatitude(), b.getLongitude(), ll.latitude(), ll.longitude());
                basis = "DETAIL_GROUND";
                if (distanceM <= MATCH_RADIUS_M) {
                    // ★≤200m만 접지 적재. >200m는 적재하지 않고 아래에서 보고 후 정지.
                    tourContentRepository.save(TourContent.create(row, ll.latitude(), ll.longitude()));
                    grounded++;
                }
            }

            SeedDistance sd = new SeedDistance(breweryId, contentId, distanceM, basis);
            distances.add(sd);
            log.info("[tour] 시드 검증 brewery_id={} content_id={} distance_m={} basis={}",
                    breweryId, contentId, Math.round(distanceM), basis);
            if (distanceM > MATCH_RADIUS_M) {
                violations.add(sd);
            } else {
                confirmed.put(breweryId, contentId);
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (SeedDistance v : violations) {
                sb.append(String.format("%n  - brewery_id=%s content_id=%s distance_m=%d basis=%s",
                        v.breweryId(), v.contentId(), Math.round(v.distanceM()), v.basis()));
            }
            // 보고 후 정지 — @Transactional 롤백으로 접지분도 되돌린다. 사람이 시드 재확정 후 재실행.
            throw new IllegalStateException(
                    "시드 200m 검증 실패 " + violations.size() + "건 — 적재하지 않고 정지(사람이 좌표 재확정 필요):" + sb);
        }

        log.info("[tour] 10단계 매칭산출 seeds={} cacheCovered={} cachedNotCovered={} grounded={} confirmed={}",
                distances.size(), cacheCovered, cachedNotCovered, grounded, confirmed.size());
        return new ResolveResult(distances.size(), cacheCovered, cachedNotCovered, grounded, confirmed, distances);
    }
}
