package com.jeontongjuro.backend.global;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 파이프라인 JSON 파싱 공용 ObjectMapper 빈.
 *
 * 등록 이유: Spring Boot 4의 spring-boot-starter-webmvc는 Jackson 자동구성을 끌어오지 않아
 * jackson-databind가 클래스패스에 있어도 ObjectMapper "빈"은 컨텍스트에 없다.
 * collect 컴포넌트가 주입받을 빈을 여기서 명시 등록한다.
 *
 * 경계: 이 빈은 파이프라인 JSON 파싱 전용이다. 웹 MVC HTTP 응답 직렬화 동작을 규정하지 않으며,
 * 그 목적의 커스터마이징을 여기에 추가하지 말 것(추후 user/auth REST 응답 포맷은 별도 구성으로 관리).
 * @Primary 미사용 — 자동구성 ObjectMapper가 존재하지 않아 충돌이 없으므로 전역 기본을 덮어쓸 이유가 없다.
 */
@Configuration
public class JacksonConfig {

    /**
     * FAIL_ON_UNKNOWN_PROPERTIES = 엔티티에 매핑되지 않은 원본 키가 나타나면 조용히 버리지 않고
     * 적재를 실패시키는 무손실 강제 장치. (종전 RawCollectService가 자체 copy()에 걸던 설정을 이관 — 유실 금지.)
     */
    @Bean
    public ObjectMapper pipelineObjectMapper() {
        return new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
