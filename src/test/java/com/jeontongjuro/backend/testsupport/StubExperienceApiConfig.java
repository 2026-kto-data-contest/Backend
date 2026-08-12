package com.jeontongjuro.backend.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트는 체험 odcloud API를 실제 호출하면 안 된다 — 운영 {@code ExperienceApiClientImpl}을 @Primary 스텁으로
 * 덮는다(StubTourApiConfig·StubGeocodingConfig 동일 패턴). 기본은 픽스처 52행을 반환한다(오케스트레이터 골든
 * 회귀에서 15단계가 결정론적으로 52건 편입되도록). 개별 테스트는 이 스텁을 autowire해 {@code returning}/
 * {@code failing}으로 동작을 바꿔 diff·skip·fail-fast 경로를 고정한다.
 */
@TestConfiguration
public class StubExperienceApiConfig {

    @Bean
    @Primary
    public StubExperienceApiClient stubExperienceApiClient(ObjectMapper objectMapper) {
        return new StubExperienceApiClient(ExperienceFixtures.rows(objectMapper));
    }
}
