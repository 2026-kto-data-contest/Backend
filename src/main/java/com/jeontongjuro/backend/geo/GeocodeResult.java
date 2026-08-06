package com.jeontongjuro.backend.geo;

import com.jeontongjuro.backend.brewery.CoordSource;
import java.math.BigDecimal;

/**
 * 지오코딩 산출 결과(geo → brewery). 범위 검증(위도 33~39·경도 124~132)을 통과하고 scale(6)로 정규화한
 * 좌표와, 어느 폴백 단계에서 성공했는지({@link CoordSource})를 담는다.
 * <p>
 * ★대입은 brewery 패키지 몫 — 이 record는 Brewery 엔티티를 참조하지 않고 breweryId(String)만 들고 있어
 * geo→brewery 역의존을 막는다(3-8 계층 원칙).
 */
public record GeocodeResult(
        String breweryId,
        BigDecimal latitude,
        BigDecimal longitude,
        CoordSource source
) {
}
