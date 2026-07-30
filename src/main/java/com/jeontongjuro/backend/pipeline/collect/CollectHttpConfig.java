package com.jeontongjuro.backend.pipeline.collect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * collect 전용 HTTP 클라이언트 구성.
 *
 * 등록 이유: Spring Boot 4의 spring-boot-starter-webmvc는 RestClient 자동구성(별도 restclient 모듈)을
 * 끌어오지 않아 RestClient.Builder 빈이 컨텍스트에 없다(ObjectMapper 빈 부재와 같은 계열).
 * OdcloudRawSnapshotSource가 주입받을 빌더를 여기서 명시 등록한다.
 *
 * 경계: 파이프라인 수집(odcloud 호출) 전용이다. user/auth 등 타 도메인 HTTP 클라이언트 설정을 여기에 넣지 말 것.
 */
@Configuration
public class CollectHttpConfig {

    @Bean
    public RestClient.Builder collectRestClientBuilder() {
        // 클래스패스의 jackson-databind 기반 기본 컨버터로 충분(JsonNode 파싱 전용 — 값 변환 없음).
        return RestClient.builder();
    }
}
