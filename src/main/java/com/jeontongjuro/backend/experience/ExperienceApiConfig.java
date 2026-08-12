package com.jeontongjuro.backend.experience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * experience(aT 체험 프로그램 15단계) 설정 활성화. RestClient.Builder 빈은 collect의 {@code CollectHttpConfig}가
 * 등록한 순수 빌더(baseUrl 미지정)를 재사용하고, 클라이언트가 {@code experience.api.api-base}로 배선한다
 * (tour/geo가 같은 공용 빌더를 재사용하는 선례와 동일 — 빌더는 공용, 배선은 각 도메인).
 */
@Configuration
@EnableConfigurationProperties(ExperienceApiProperties.class)
public class ExperienceApiConfig {
}
