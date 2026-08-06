package com.jeontongjuro.backend.geo;

import com.jeontongjuro.backend.brewery.CoordSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 지오코딩 산출 — 폴백 체인·시드 적용·범위 검증을 엮어 한 양조장의 좌표를 계산한다(호출·파싱은
 * {@link KakaoGeocodingClient}, 대입은 brewery 패키지). 2단 폴백(#28 3-3):
 * <pre>
 *   ① 시드에 brewery_id가 있으면 → 시드의 fixed_address, source=KAKAO_ADDRESS_SEED (시드 최우선)
 *   ② 없으면 raw address 그대로     → 성공 시 source=KAKAO_ADDRESS
 *   ③ 실패 시 정규화 규칙 적용 재시도 → 성공 시 source=KAKAO_ADDRESS_NORMALIZED
 *   ④ 그래도 실패 → empty(호출자가 fail 카운트 + 명시 로그). 예외를 삼키지 않는다.
 * </pre>
 * ★시드 대상은 raw로 조회하면 실패·오답이므로 시드 경로에서 raw로 폴백하지 않는다.
 * <p>
 * 범위 검증(위도 33~39·경도 124~132)은 저장 직전 필수다 — x/y 축 전도는 Haversine에서 조용히 틀린
 * 거리를 내므로, 벗어나면 저장하지 않고 fail-fast로 파이프라인을 중단시킨다(경고 아님).
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LNG_MIN = 124.0;
    private static final double LNG_MAX = 132.0;
    private static final int COORD_SCALE = 6;

    private final KakaoGeocodingClient client;
    private final AddressFixSeedLoadService seedLoader;

    public GeocodingService(KakaoGeocodingClient client, AddressFixSeedLoadService seedLoader) {
        this.client = client;
        this.seedLoader = seedLoader;
    }

    /**
     * 한 양조장의 좌표를 산출한다. 좌표를 얻지 못하면 empty(호출자가 fail 처리).
     * 범위 이탈 시 {@link IllegalStateException}으로 파이프라인을 중단시킨다.
     *
     * @param breweryId  대상 양조장(로그·결과 라벨)
     * @param rawAddress 현재 brewery.address(raw 원문 — 절대 수정하지 않음)
     */
    public Optional<GeocodeResult> geocode(String breweryId, String rawAddress) {
        // ① 시드 최우선.
        Optional<String> seedFixed = seedLoader.resolve(breweryId, rawAddress);
        if (seedFixed.isPresent()) {
            return query(breweryId, seedFixed.get(), CoordSource.KAKAO_ADDRESS_SEED);
        }

        // ② raw 그대로.
        Optional<GeocodeResult> raw = query(breweryId, rawAddress, CoordSource.KAKAO_ADDRESS);
        if (raw.isPresent()) {
            return raw;
        }

        // ③ 정규화 규칙 폴백.
        for (String candidate : AddressNormalizer.candidates(rawAddress)) {
            Optional<GeocodeResult> normalized = query(breweryId, candidate, CoordSource.KAKAO_ADDRESS_NORMALIZED);
            if (normalized.isPresent()) {
                log.info("[geo] 정규화 성공 brewery_id={} raw='{}' → '{}'", breweryId, rawAddress, candidate);
                return normalized;
            }
        }

        // ④ 전부 실패.
        return Optional.empty();
    }

    /** 카카오 조회 + 범위 검증 → GeocodeResult. 무결과면 empty, 범위 이탈이면 fail-fast 예외. */
    private Optional<GeocodeResult> query(String breweryId, String address, CoordSource source) {
        Optional<KakaoAddressMatch> match = client.geocode(address);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        KakaoAddressMatch m = match.get();
        double lat = m.lat().doubleValue();
        double lng = m.lng().doubleValue();
        if (lat < LAT_MIN || lat > LAT_MAX || lng < LNG_MIN || lng > LNG_MAX) {
            // 저장하지 말고 파이프라인 중단 — 축 전도/이상 좌표 전건 검출(두 범위 비중첩).
            throw new IllegalStateException(String.format(
                    "좌표 범위 이탈(축 전도 의심) brewery_id=%s source=%s address='%s' lat=%s lng=%s "
                            + "(허용 위도 %s~%s·경도 %s~%s)",
                    breweryId, source, address, m.lat(), m.lng(), LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX));
        }
        BigDecimal latitude = m.lat().setScale(COORD_SCALE, RoundingMode.HALF_UP);
        BigDecimal longitude = m.lng().setScale(COORD_SCALE, RoundingMode.HALF_UP);
        return Optional.of(new GeocodeResult(breweryId, latitude, longitude, source));
    }
}
