package com.jeontongjuro.backend.geo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * geo 지오코딩 설정 활성화. RestClient.Builder 빈은 collect의 {@code CollectHttpConfig}가
 * 등록한 것을 재사용한다(baseUrl 미지정 순수 빌더 — 클라이언트가 {@code geo.kakao.base-url}로 배선).
 */
@Configuration
@EnableConfigurationProperties(GeoProperties.class)
public class GeoConfig {
}
