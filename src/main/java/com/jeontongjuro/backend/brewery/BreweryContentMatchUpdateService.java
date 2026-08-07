package com.jeontongjuro.backend.brewery;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10단계 콘텐츠 매칭 대입(brewery 계층). 산출({@code TourMatchResolveService})이 200m 검증·접지까지 마친
 * 확정 맵({@code brewery_id → content_id})을 받아 brewery.content_id를 대입만 한다(3-8 계층 원칙: 산출=tour,
 * 대입=brewery). 8단계 좌표 대입({@code BreweryCoordinateUpdateService})과 대칭.
 * <p>
 * FK 안전: confirmed의 content_id는 산출 단계가 tour_content에 커밋(접지 or 캐시)해 둔 값뿐이라
 * brewery.content_id → tour_content FK가 항상 만족된다(단계별 독립 커밋 순서: 산출 먼저).
 * <p>
 * 멱등: 이미 같은 content_id면 unchanged. 항등식 {@code matched + unmatched == breweries(==59)}.
 */
@Service
public class BreweryContentMatchUpdateService {

    private static final Logger log = LoggerFactory.getLogger(BreweryContentMatchUpdateService.class);

    private final BreweryRepository breweryRepository;

    public BreweryContentMatchUpdateService(BreweryRepository breweryRepository) {
        this.breweryRepository = breweryRepository;
    }

    /**
     * @param breweries 처리 대상 양조장 수
     * @param matched   확정 맵에 있어 content_id가 채워진 양조장 수
     * @param unmatched 확정 맵에 없어 content_id가 비는 양조장 수 (matched + unmatched == breweries)
     * @param changed   이번 실행에서 새로 대입한 수
     * @param unchanged 이미 같은 content_id라 건너뛴 수(멱등)
     */
    public record MatchResult(int breweries, int matched, int unmatched, int changed, int unchanged) {
    }

    @Transactional
    public MatchResult apply(Map<String, String> confirmed) {
        int breweries = 0;
        int matched = 0;
        int unmatched = 0;
        int changed = 0;
        int unchanged = 0;

        for (Brewery b : breweryRepository.findAll()) {
            breweries++;
            String contentId = confirmed.get(b.getBreweryId());
            if (contentId == null) {
                unmatched++;
                continue;
            }
            matched++;
            if (Objects.equals(contentId, b.getContentId())) {
                unchanged++;
            } else {
                b.applyContentMatch(contentId, OffsetDateTime.now(ZoneOffset.UTC));
                changed++;
            }
        }

        if (matched + unmatched != breweries) {
            throw new IllegalStateException(String.format(
                    "항등식 위반 matched(%d)+unmatched(%d) != breweries(%d)", matched, unmatched, breweries));
        }
        log.info("[tour] 10단계 매칭대입 breweries={} matched={} unmatched={} (changed={} unchanged={})",
                breweries, matched, unmatched, changed, unchanged);
        return new MatchResult(breweries, matched, unmatched, changed, unchanged);
    }
}
