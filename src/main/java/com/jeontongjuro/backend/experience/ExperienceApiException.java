package com.jeontongjuro.backend.experience;

/**
 * odcloud 체험 API 호출/파싱 실패 신호. ★이 예외만 15단계 skip 사유가 된다 — {@link ExperienceRollupService}가
 * 이것을 잡아 15단계만 건너뛰고(기존 행 보존) 파이프라인 나머지를 완주시킨다. 체험은 기준일 2021-09-17로 고정된
 * 정적 파생 데이터라 외부 API 장애가 파이프라인 전체를 죽이면 안 된다(심사 데모 안정성).
 * <p>
 * ★시드 미매칭(API 성공 후 시드에 없는 양조장명)은 이 예외가 아니라 {@code IllegalStateException}으로 던져
 * fail-fast시킨다 — 그건 원본 갱신 신호라 사람이 봐야 한다. 두 경우를 코드에서 명확히 구분한다.
 * <p>
 * 메시지·원인에 serviceKey를 싣지 않는다(응답 에코 차단).
 */
public class ExperienceApiException extends RuntimeException {

    public ExperienceApiException(String message) {
        super(message);
    }

    public ExperienceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
