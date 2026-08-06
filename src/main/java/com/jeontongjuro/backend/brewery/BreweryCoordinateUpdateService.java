package com.jeontongjuro.backend.brewery;

import com.jeontongjuro.backend.geo.GeocodeResult;
import com.jeontongjuro.backend.geo.GeocodingService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 산출된 좌표를 brewery에 대입하는 단계(#28 8단계, brewery 패키지). 산출은 {@link GeocodingService}(geo)가
 * 하고, 이 서비스는 brewery 상태를 읽어 멱등 skip을 판정하고 대입만 한다(3-8 계층 원칙: 산출=도메인,
 * 대입=brewery).
 * <p>
 * 멱등: 좌표가 이미 있으면({@code latitude != null}) 카카오를 호출하지 않는다. region 갱신 단계와 동일하게
 * {@code @Transactional} 단일 커밋이다(단계별 독립 커밋 — 실패 시 이 단계만 롤백, 재실행하면 멱등 재개).
 */
@Service
public class BreweryCoordinateUpdateService {

    private static final Logger log = LoggerFactory.getLogger(BreweryCoordinateUpdateService.class);

    private final BreweryRepository breweryRepository;
    private final GeocodingService geocodingService;

    public BreweryCoordinateUpdateService(BreweryRepository breweryRepository,
                                          GeocodingService geocodingService) {
        this.breweryRepository = breweryRepository;
        this.geocodingService = geocodingService;
    }

    /**
     * 카운트 의미를 한 가지로 고정한다({@code geocoded + skipped + failed == total}):
     * @param total    처리 대상 brewery 수
     * @param geocoded 이번 실행에서 카카오를 호출해 새로 저장한 건수
     * @param skipped  좌표가 이미 있어 호출하지 않은 건수
     * @param failed   호출했으나 좌표를 얻지 못한 건수
     */
    public record GeoResult(int total, int geocoded, int skipped, int failed) {
    }

    @Transactional
    public GeoResult apply() {
        int geocoded = 0;
        int skipped = 0;
        int failed = 0;
        for (Brewery b : breweryRepository.findAll()) {
            // 멱등: 이미 좌표가 있으면 카카오 미호출.
            if (b.getLatitude() != null) {
                skipped++;
                continue;
            }
            Optional<GeocodeResult> result = geocodingService.geocode(b.getBreweryId(), b.getAddress());
            if (result.isPresent()) {
                GeocodeResult g = result.get();
                b.applyCoordinate(g.latitude(), g.longitude(), g.source(),
                        OffsetDateTime.now(ZoneOffset.UTC));
                geocoded++;
            } else {
                failed++;
                log.warn("[geo] 좌표 획득 실패 brewery_id={} address='{}'", b.getBreweryId(), b.getAddress());
            }
        }
        int total = geocoded + skipped + failed;
        log.info("[geo] geocoded={} skipped={} failed={} (total={})", geocoded, skipped, failed, total);
        return new GeoResult(total, geocoded, skipped, failed);
    }
}
