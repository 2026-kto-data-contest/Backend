package com.jeontongjuro.backend.tour;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * TourAPI 좌표 산출·검증(mapx=경도/mapy=위도 → latitude/longitude). geo {@code GeocodingService}의 범위
 * 검증과 동일 상수·동일 정책:
 * <ul>
 *   <li>파싱 불가·빈 문자열 → {@link Optional#empty()}(호출자가 좌표 없는 콘텐츠로 skip·warn 처리).</li>
 *   <li>파싱되나 범위 이탈(위도 33~39·경도 124~132 밖) → {@link IllegalStateException} fail-fast.
 *       두 범위가 비중첩이라 x/y 축 전도를 전건 검출한다(조용히 틀린 거리 방지).</li>
 * </ul>
 * ★대표 좌표는 언제나 brewery.latitude/longitude이며, 이 값은 tour_content에만 저장된다(brewery로 승격 금지).
 */
public final class TourGeoValidator {

    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LNG_MIN = 124.0;
    private static final double LNG_MAX = 132.0;
    private static final int COORD_SCALE = 6;

    private TourGeoValidator() {
    }

    /** 위도/경도 검증 결과(scale 6 정규화). */
    public record LatLng(BigDecimal latitude, BigDecimal longitude) {
    }

    /**
     * mapx(경도)·mapy(위도) 문자열을 검증해 LatLng로. 빈/파싱불가면 empty, 범위 이탈이면 fail-fast.
     *
     * @param label 로그·예외 라벨(content_id 등)
     * @param mapx  경도 원문 문자열
     * @param mapy  위도 원문 문자열
     */
    public static Optional<LatLng> validate(String label, String mapx, String mapy) {
        if (mapx == null || mapx.isBlank() || mapy == null || mapy.isBlank()) {
            return Optional.empty();
        }
        BigDecimal lng;
        BigDecimal lat;
        try {
            lng = new BigDecimal(mapx.trim());
            lat = new BigDecimal(mapy.trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        double latD = lat.doubleValue();
        double lngD = lng.doubleValue();
        if (latD < LAT_MIN || latD > LAT_MAX || lngD < LNG_MIN || lngD > LNG_MAX) {
            throw new IllegalStateException(String.format(
                    "TourAPI 좌표 범위 이탈(축 전도 의심) label=%s mapx(경도)=%s mapy(위도)=%s "
                            + "(허용 위도 %s~%s·경도 %s~%s)",
                    label, mapx, mapy, LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX));
        }
        return Optional.of(new LatLng(
                lat.setScale(COORD_SCALE, RoundingMode.HALF_UP),
                lng.setScale(COORD_SCALE, RoundingMode.HALF_UP)));
    }

    /**
     * 두 좌표 간 Haversine 거리(m). 미커버 시드의 detailCommon2 응답 좌표를 brewery 좌표와 대조해 200m
     * 검증할 때 쓴다(캐시 커버분은 brewery_nearby.distance_m을 직접 쓰므로 이 계산 불필요).
     */
    public static double haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double r = 6371000.0;
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return r * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
