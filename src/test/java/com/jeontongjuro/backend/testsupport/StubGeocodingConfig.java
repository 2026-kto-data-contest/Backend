package com.jeontongjuro.backend.testsupport;

import com.jeontongjuro.backend.geo.KakaoAddressMatch;
import com.jeontongjuro.backend.geo.KakaoGeocodingClient;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트는 카카오를 실제 호출하면 안 된다 — 운영 {@code KakaoGeocodingClientImpl}을 @Primary 스텁으로 덮는다.
 * <p>
 * 스텁은 어떤 주소든 한국 범위 안(서울 시청 부근)의 고정 좌표를 돌려준다. 폴백 체인 첫 시도(raw/시드)가
 * 항상 성공하므로 오케스트레이터 8단계가 실행돼도 네트워크·범위 이탈 없이 멱등·오염 검증만 가능하다.
 * (coord_source 실측 분포 53/2/4·시드 실좌표는 실기동 산물인 골든 TSV로 별도 검증한다 — 스텁은 분포를
 * 재현하지 않는다.)
 */
@TestConfiguration
public class StubGeocodingConfig {

    /** 서울 시청 부근 — 위도 33~39·경도 124~132 범위 내. */
    private static final BigDecimal STUB_LAT = new BigDecimal("37.566500");
    private static final BigDecimal STUB_LNG = new BigDecimal("126.978000");

    @Bean
    @Primary
    public KakaoGeocodingClient stubKakaoGeocodingClient() {
        return query -> Optional.of(new KakaoAddressMatch(STUB_LAT, STUB_LNG, "ROAD_ADDR"));
    }
}
