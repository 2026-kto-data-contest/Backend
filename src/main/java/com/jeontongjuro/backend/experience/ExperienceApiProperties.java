package com.jeontongjuro.backend.experience;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * aT 체험 프로그램 odcloud API 설정(application.yml {@code experience.api}). tour/geo처럼 도메인 전용
 * 네임스페이스 — {@code pipeline.collect.*}(raw 수집용)와 성격이 달라 재사용하지 않는다(프로퍼티 충돌 금지).
 * <p>
 * dataset-id·uddi는 하드코딩하지 않고 여기서만 온다. service-key는 환경변수 AT_SERVICE_KEY(aT·KTO 공용)로만
 * 주입하며 키 문자열을 코드·yml·로그·예외 메시지에 절대 싣지 않는다. 미주입 시 15단계 첫 호출에서 명확히 중단.
 */
@ConfigurationProperties(prefix = "experience.api")
public record ExperienceApiProperties(
        String apiBase,
        String serviceKey,
        String datasetId,
        String uddi,
        int perPage
) {

    /** odcloud 경로 형식(collect와 동일): /{dataset_id}/v1/uddi:{uddi} */
    public String path() {
        return "/" + datasetId + "/v1/uddi:" + uddi;
    }
}
