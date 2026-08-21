package com.jeontongjuro.backend.tour;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.PhoneSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 12단계(#50) — 관광공사 detailCommon2/detailIntro2로 상세를 편입한다. content_id가 확정된 양조장(10단계 산물,
 * 19곳)만 대상이며, 각 콘텐츠에서 소개글(overview)·운영시간·휴무·주차·수용인원·전화(infocenter)를 받아
 * <b>기존 행에 UPDATE</b>한다.
 * <p>
 * ★백필 함정 방어: tour_content 10,279행·brewery 59행이 이미 존재해 upsert/마스터 로드는 이들을 건너뛴다
 * (도수 #36 사고 경로). 그래서 이 단계는 별도 UPDATE 경로로만 값을 채운다.
 * <ul>
 *   <li>overview → tour_content.backfillOverview (upsert가 손대지 않는 필드라 재수집에도 보존).</li>
 *   <li>운영시간~수용인원 → brewery.applyTourDetail. 전화 → brewery.applyPhone(TOUR).</li>
 * </ul>
 * 멱등: overview는 {@code overviewFetchedAt}, 상세는 {@code operating_hours}가 이미 있으면 API를 호출하지 않는다
 * (좌표 단계와 동일한 "이미 있으면 skip"). {@code @Transactional} 단일 커밋(단계 독립 커밋).
 * <p>
 * 전화 우선순위 TOUR &gt; KAKAO는 순서로 강제한다 — 이 단계가 먼저 TOUR로 채우고, 13단계 카카오 시드는
 * {@code phone == null}인 곳만 보충한다.
 */
@Service
public class TourDetailEnrichService {

    private static final Logger log = LoggerFactory.getLogger(TourDetailEnrichService.class);

    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;
    private final TourApiClient tourApiClient;

    public TourDetailEnrichService(BreweryRepository breweryRepository,
                                   TourContentRepository tourContentRepository,
                                   TourApiClient tourApiClient) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
        this.tourApiClient = tourApiClient;
    }

    /**
     * 카운트 의미 고정:
     * @param contentBreweries content_id가 있어 처리 대상이 된 양조장 수(기대 19)
     * @param overviewBackfilled 이번에 소개글을 새로 채운 tour_content 수
     * @param overviewSkipped    이미 채워져(fetchedAt) 건너뛴 수
     * @param overviewMissing    호출했으나 소개글이 비어 못 채운 수
     * @param tourDetailUpdated  운영시간 등 상세를 새로 채운 brewery 수
     * @param tourDetailSkipped  이미 채워져 건너뛴 수
     * @param phoneFromTour      infocenter로 전화를 채운 수(phone_source=TOUR)
     */
    public record EnrichResult(int contentBreweries, int overviewBackfilled, int overviewSkipped,
                               int overviewMissing, int tourDetailUpdated, int tourDetailSkipped,
                               int phoneFromTour) {
    }

    @Transactional
    public EnrichResult enrich() {
        int contentBreweries = 0;
        int overviewBackfilled = 0;
        int overviewSkipped = 0;
        int overviewMissing = 0;
        int tourDetailUpdated = 0;
        int tourDetailSkipped = 0;
        int phoneFromTour = 0;

        for (Brewery b : breweryRepository.findAll()) {
            String contentId = b.getContentId();
            if (contentId == null) {
                continue; // 미매칭 양조장은 관광공사 상세 대상 아님
            }
            contentBreweries++;
            TourContent tc = tourContentRepository.findById(contentId).orElse(null);
            if (tc == null) {
                // FK 상 발생 불가지만 방어 — 상세 없이 넘어감
                log.warn("[tour-detail] content_id={} 에 tour_content 없음 — brewery_id={} 상세 skip",
                        contentId, b.getBreweryId());
                continue;
            }

            // (1) 소개글 백필 — tour_content
            if (tc.isOverviewFetched()) {
                overviewSkipped++;
            } else {
                Optional<String> overview = tourApiClient.detailOverview(contentId);
                if (overview.isPresent()) {
                    tc.backfillOverview(overview.get(), OffsetDateTime.now(ZoneOffset.UTC));
                    overviewBackfilled++;
                } else {
                    overviewMissing++;
                    log.warn("[tour-detail] overview 비어있음 content_id={}", contentId);
                }
            }

            // (2) 운영시간·휴무·주차·수용인원·전화(TOUR) — brewery
            // DEBT-23: operating_hours 한 필드 유무로 상세 4필드(운영시간·휴무·주차·수용인원)를 통째 skip한다(단일필드 게이트).
            //          새 상세 컬럼을 추가하면 이미 operating_hours가 찬 기존 행은 영구 NULL — 전용 백필 UPDATE 경로를 함께 넣어라. docs/DEBT.md #23
            if (b.getOperatingHours() != null) {
                tourDetailSkipped++;
            } else {
                Optional<TourIntro> intro = tourApiClient.detailIntro(contentId, tc.getContentTypeId());
                if (intro.isPresent()) {
                    TourIntro i = intro.get();
                    b.applyTourDetail(i.operatingHours(), i.restDate(), i.parkingInfo(), i.accomCount());
                    if (i.phone() != null) {
                        b.applyPhone(i.phone(), PhoneSource.TOUR);
                        phoneFromTour++;
                    }
                    tourDetailUpdated++;
                } else {
                    log.warn("[tour-detail] detailIntro2 결과 없음 content_id={}", contentId);
                }
            }
        }
        log.info("[tour-detail] 대상 {} · overview(채움 {}/skip {}/없음 {}) · 상세(채움 {}/skip {}) · 전화TOUR {}",
                contentBreweries, overviewBackfilled, overviewSkipped, overviewMissing,
                tourDetailUpdated, tourDetailSkipped, phoneFromTour);
        return new EnrichResult(contentBreweries, overviewBackfilled, overviewSkipped, overviewMissing,
                tourDetailUpdated, tourDetailSkipped, phoneFromTour);
    }
}
