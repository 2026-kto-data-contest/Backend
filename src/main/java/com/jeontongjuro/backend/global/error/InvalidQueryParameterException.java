package com.jeontongjuro.backend.global.error;

/**
 * 요청 쿼리 파라미터 값이 허용 집합 밖일 때 던지는 단일 검증 예외(400 매핑).
 * <p>
 * ★필터값 검증은 이 예외 하나로 수렴한다 — 파라미터별 커스텀 예외를 늘리지 않는다.
 * 던지는 쪽이 사람이 읽을 수 있는 완결된 메시지(어떤 파라미터·무슨 값·허용 집합)를 만들어 전달하고,
 * 코드 문자열은 {@link GlobalExceptionHandler}가 부여한다.
 */
public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}
