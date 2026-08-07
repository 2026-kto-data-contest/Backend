package com.jeontongjuro.backend.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TourAPI 좌표 검증·거리 계산 순수 단위(Spring·DB 불필요). */
class TourGeoValidatorTest {

    @Test
    @DisplayName("정상 좌표: mapx=경도·mapy=위도로 파싱, scale 6")
    void validCoords() {
        Optional<TourGeoValidator.LatLng> r =
                TourGeoValidator.validate("c1", "126.978000", "37.566500");
        assertThat(r).isPresent();
        assertThat(r.get().latitude()).isEqualByComparingTo("37.566500");
        assertThat(r.get().longitude()).isEqualByComparingTo("126.978000");
    }

    @Test
    @DisplayName("빈/파싱불가 좌표는 empty(호출자가 skip)")
    void blankOrUnparsable() {
        assertThat(TourGeoValidator.validate("c", "", "37.5")).isEmpty();
        assertThat(TourGeoValidator.validate("c", "126.9", null)).isEmpty();
        assertThat(TourGeoValidator.validate("c", "abc", "37.5")).isEmpty();
    }

    @Test
    @DisplayName("축 전도(mapx/mapy 뒤바뀜)는 범위 이탈로 fail-fast")
    void axisFlipFailsFast() {
        // 올바른 호출은 mapx=경도(126.978)·mapy=위도(37.5665). 뒤바꿔 넣으면 위도=126.978>39 → 예외.
        assertThatThrownBy(() -> TourGeoValidator.validate("c", "37.566500", "126.978000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("범위 이탈");
    }

    @Test
    @DisplayName("Haversine: 동일점 0m, 위도 0.001도≈111m")
    void haversine() {
        BigDecimal lat = new BigDecimal("37.566500");
        BigDecimal lng = new BigDecimal("126.978000");
        assertThat(TourGeoValidator.haversineMeters(lat, lng, lat, lng)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
        double d = TourGeoValidator.haversineMeters(lat, lng, new BigDecimal("37.567500"), lng);
        assertThat(d).isCloseTo(111.0, org.assertj.core.data.Offset.offset(2.0));
    }
}
