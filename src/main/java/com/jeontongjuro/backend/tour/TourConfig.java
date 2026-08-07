package com.jeontongjuro.backend.tour;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * tour(TourAPI 근접 캐싱·매칭) 설정 활성화. RestClient.Builder 빈은 collect의 {@code CollectHttpConfig}가
 * 등록한 순수 빌더(baseUrl 미지정)를 재사용한다 — 클라이언트가 {@code tour.api.base-url}로 배선한다.
 * geo가 같은 빌더를 재사용하는 선례와 동일(도메인 경계: collect 빌더는 공용, 배선은 각 도메인).
 */
@Configuration
@EnableConfigurationProperties(TourProperties.class)
public class TourConfig {
}
