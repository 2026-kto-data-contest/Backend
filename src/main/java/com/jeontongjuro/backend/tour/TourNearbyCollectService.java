package com.jeontongjuro.backend.tour;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 9단계 근접 캐싱(산출, tour 계층). 59 양조장 각각의 대표 좌표(brewery.latitude/longitude)로
 * locationBasedList2를 호출해 반경 내 콘텐츠를 tour_content에 upsert하고 (양조장,콘텐츠) 쌍을
 * brewery_nearby에 적재한다. contentTypeId 미지정(전 타입).
 * <p>
 * ★대표 좌표는 언제나 brewery 좌표다 — TourAPI 좌표는 tour_content에만 저장하고 brewery로 승격하지 않는다.
 * 멱등: 콘텐츠는 modifiedtime 변경 시에만 갱신(동일 시 unchanged), brewery_nearby는 (양조장,콘텐츠)
 * 존재 시 unchanged. 재실행해도 좌표가 고정이라 결과가 같다.
 * <p>
 * 항등식 {@code withContent + emptyRadius == breweries(==59)}. 전 양조장이 좌표를 가진다는 전제를
 * 강제한다(좌표 없으면 fail-fast — 8단계 정합성 신호).
 * <p>
 * ★카운트 항등식(부채 #3 정정): content upsert 카운터는 (양조장,콘텐츠) 쌍마다 증가한다(distinct 콘텐츠가
 * 아니라 쌍 단위 순회 수). nearby 카운터와 같은 내부 루프에서 증가하므로 다음이 항상 성립한다:
 * <pre>
 *   contentUpsert(Inserted+Updated+Unchanged) == nearby(Inserted+Unchanged) == brewery_nearby 행수
 * </pre>
 * {@code distinctContentTotal}(=이번 실행이 커버한 distinct content_id 수)은 위 항등식에 포함되지 않는
 * 별도 지표이며, 사람이 실제로 보고 싶어하는 tour_content 고유 행수에 해당한다. 초회 실행에서
 * {@code contentUpsertInserted}가 distinct 신규 콘텐츠 수와 우연히 일치할 수 있으나(첫 등장 때만 insert,
 * 재등장은 같은 실행 내 unchanged로 빠지므로), 재실행 시에는 전량 unchanged가 되어 일치하지 않는다.
 */
@Service
public class TourNearbyCollectService {

    private static final Logger log = LoggerFactory.getLogger(TourNearbyCollectService.class);

    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;
    private final BreweryNearbyRepository nearbyRepository;
    private final TourApiClient client;
    private final TourProperties properties;

    public TourNearbyCollectService(BreweryRepository breweryRepository,
                                    TourContentRepository tourContentRepository,
                                    BreweryNearbyRepository nearbyRepository,
                                    TourApiClient client,
                                    TourProperties properties) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
        this.nearbyRepository = nearbyRepository;
        this.client = client;
        this.properties = properties;
    }

    /**
     * 카운트 의미 고정. content upsert 카운터는 <b>(양조장,콘텐츠) 쌍 단위 순회 수</b>이지 distinct 콘텐츠가 아니다.
     * @param breweries          처리 대상 양조장 수
     * @param withContent        반경 내 콘텐츠가 1건 이상 캐시된 양조장 수
     * @param emptyRadius        반경 내 콘텐츠가 0건인 양조장 수 (withContent + emptyRadius == breweries)
     * @param contentUpsertInserted   새로 insert한 tour_content upsert 시도(쌍 단위, distinct 아님)
     * @param contentUpsertUpdated    modifiedtime 변경으로 update한 upsert 시도(쌍 단위)
     * @param contentUpsertUnchanged  modifiedtime 동일로 건너뛴 upsert 시도(쌍 단위, 멱등)
     * @param contentSkippedNoCoord 좌표 파싱 불가로 적재 못 한 콘텐츠(warn)
     * @param distinctContentTotal 이번 실행이 커버한 distinct content_id 수(별도 지표, 위 upsert 항등식과 무관)
     * @param nearbyInserted     새로 적재한 brewery_nearby 쌍
     * @param nearbyUnchanged    이미 있던 brewery_nearby 쌍(멱등)
     */
    public record NearbyResult(int breweries, int withContent, int emptyRadius,
                               int contentUpsertInserted, int contentUpsertUpdated, int contentUpsertUnchanged,
                               int contentSkippedNoCoord,
                               int distinctContentTotal,
                               int nearbyInserted, int nearbyUnchanged) {
    }

    @Transactional
    public NearbyResult collect() {
        int breweries = 0;
        int withContent = 0;
        int emptyRadius = 0;
        int cInserted = 0;
        int cUpdated = 0;
        int cUnchanged = 0;
        int cSkippedNoCoord = 0;
        int nInserted = 0;
        int nUnchanged = 0;
        // distinct content_id 커버리지(쌍 단위 upsert 카운터와 별개 지표). 좌표 검증 통과분만 집계.
        java.util.Set<String> distinctContentIds = new java.util.HashSet<>();

        // DEBT-17: 이 루프는 매 실행 전 양조장에 대해 locationBasedList를 라이브 재호출한다(멱등 skip은 DB write만,
        //          API 호출은 항상). delete 경로가 없어 원본에서 사라진 콘텐츠(유령)는 tour_content에 남는다.
        //          ★process 재실행 = 사람이 봉인한 유령 정리 상태 파손 + 외부 API 재스캔(~4분). 함부로 재실행 말 것. docs/DEBT.md #17
        for (Brewery b : breweryRepository.findAll()) {
            breweries++;
            if (b.getLatitude() == null || b.getLongitude() == null) {
                throw new IllegalStateException(
                        "9단계는 전 양조장 좌표를 전제 — brewery_id=" + b.getBreweryId() + " 좌표 없음(8단계 확인)");
            }
            List<TourContentRow> rows =
                    client.locationBasedList(b.getLatitude(), b.getLongitude(), properties.radiusM());
            int nearbyForThis = 0;
            for (TourContentRow row : rows) {
                if (row.contentId() == null) {
                    continue; // contentid 없는 이상 행 방어
                }
                Optional<TourGeoValidator.LatLng> coord =
                        TourGeoValidator.validate(row.contentId(), row.mapx(), row.mapy());
                if (coord.isEmpty()) {
                    cSkippedNoCoord++;
                    log.warn("[tour] 좌표 없는 콘텐츠 skip content_id={} title={} (brewery_id={})",
                            row.contentId(), row.title(), b.getBreweryId());
                    continue;
                }
                TourGeoValidator.LatLng ll = coord.get();
                distinctContentIds.add(row.contentId());

                // tour_content upsert (modifiedtime 변경 감지). 아래 카운터는 (양조장,콘텐츠) 쌍마다 증가.
                Optional<TourContent> existing = tourContentRepository.findById(row.contentId());
                if (existing.isEmpty()) {
                    tourContentRepository.save(TourContent.create(row, ll.latitude(), ll.longitude()));
                    cInserted++;
                } else if (existing.get().isUnchanged(row.modifiedTime())) {
                    cUnchanged++;
                } else {
                    existing.get().applyUpdate(row, ll.latitude(), ll.longitude());
                    cUpdated++;
                }

                // brewery_nearby upsert (양조장,콘텐츠 쌍).
                if (!nearbyRepository.existsByBreweryIdAndContentId(b.getBreweryId(), row.contentId())) {
                    BigDecimal dist = row.distanceM() == null ? null : BigDecimal.valueOf(row.distanceM());
                    nearbyRepository.save(BreweryNearby.create(
                            b.getBreweryId(), row.contentId(), dist, properties.radiusM()));
                    nInserted++;
                } else {
                    nUnchanged++;
                }
                nearbyForThis++;
            }
            if (nearbyForThis > 0) {
                withContent++;
            } else {
                emptyRadius++;
                log.info("[tour] 반경 내 콘텐츠 0건 brewery_id={} ({})", b.getBreweryId(), b.getBusinessName());
            }
        }

        if (withContent + emptyRadius != breweries) {
            throw new IllegalStateException(String.format(
                    "항등식 위반 withContent(%d)+emptyRadius(%d) != breweries(%d)",
                    withContent, emptyRadius, breweries));
        }
        // 항등식: contentUpsert(ins+upd+unch) == nearby(ins+unch) == brewery_nearby 행수. distinctContent는 별도.
        log.info("[tour] 9단계 근접캐싱 breweries={} withContent={} emptyRadius={} | contentUpsert(pair-wise, not distinct) ins={} upd={} unch={} noCoord={} | distinctContent={} | nearby ins={} unch={}",
                breweries, withContent, emptyRadius, cInserted, cUpdated, cUnchanged, cSkippedNoCoord,
                distinctContentIds.size(), nInserted, nUnchanged);
        return new NearbyResult(breweries, withContent, emptyRadius,
                cInserted, cUpdated, cUnchanged, cSkippedNoCoord, distinctContentIds.size(), nInserted, nUnchanged);
    }
}
