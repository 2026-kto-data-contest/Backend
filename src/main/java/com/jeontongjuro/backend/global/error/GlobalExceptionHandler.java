package com.jeontongjuro.backend.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 백엔드 공용 예외 → 에러 바디({@link ErrorResponse}) 변환 어드바이스(2인 공용, global 소속).
 * <p>
 * ★이번 PR이 실제로 던지는 예외만 등록한다:
 * <ul>
 *   <li>{@link InvalidQueryParameterException} — 허용 집합 밖 필터값(직접 검증) → 400</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — page·size 등 타입 불일치(Spring 바인딩 실패) → 400</li>
 * </ul>
 * 리소스 없음(404)은 이번 PR에서 발생 상황이 없어 만들지 않는다(필요해질 때 추가).
 * <p>
 * 인증/인가(401/403) 예외는 인증 라인에서 핸들러 메서드를 추가한다(여기 자리만 비워둠 — by 인증 담당).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_QUERY_PARAMETER = "INVALID_QUERY_PARAMETER";

    @ExceptionHandler(InvalidQueryParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQueryParameter(InvalidQueryParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(INVALID_QUERY_PARAMETER, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "요청 파라미터 '" + e.getName() + "' 의 형식이 올바르지 않습니다.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(INVALID_QUERY_PARAMETER, message));
    }

    // 인증/인가(401/403) 예외는 인증 라인에서 핸들러 추가 (자리만 비워둠 — 인증 담당)
}
