package com.jeontongjuro.backend.global.error;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
 *   <li>{@link BreweryNotFoundException} — 상세 조회 시 없는 breweryId(경로 변수) → 404</li>
 * </ul>
 * <p>
 * 인증 서비스 예외와 요청 바디 검증 오류도 같은 {@code ErrorResponse} 계약으로 변환한다.
 * Spring Security 필터에서 직접 발생하는 401/403은 security/handler에서 동일한 응답 형식을 사용한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_QUERY_PARAMETER = "INVALID_QUERY_PARAMETER";
    private static final String BREWERY_NOT_FOUND = "BREWERY_NOT_FOUND";

    @ExceptionHandler(BreweryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBreweryNotFound(BreweryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(BREWERY_NOT_FOUND, e.getMessage()));
    }

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

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", "요청값을 확인해 주세요."));
    }
}
