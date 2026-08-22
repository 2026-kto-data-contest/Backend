package com.jeontongjuro.backend.global.error;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import com.jeontongjuro.backend.search.recent.RecentSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
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
 * <p>
 * ★위 특정 예외에 걸리지 않은 <b>예기치 못한</b> 예외는 {@link #handleUnexpected}가 500 +
 * {@code ErrorResponse}({@code INTERNAL_SERVER_ERROR})로 감싼다(부채 #22). 단, 스프링 MVC/서블릿이
 * 정의한 요청 예외(405·415·406·바디 파싱 실패·타입 불일치 등)는 이미 올바른 4xx로 처리되므로
 * 삼키지 않고 스프링 기본 처리에 그대로 위임한다 — 상태코드·바디 불변.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_QUERY_PARAMETER = "INVALID_QUERY_PARAMETER";
    private static final String BREWERY_NOT_FOUND = "BREWERY_NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

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

    @ExceptionHandler(RecentSearchException.class)
    public ResponseEntity<ErrorResponse> handleRecentSearchException(RecentSearchException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", "요청값을 확인해 주세요."));
    }

    /**
     * 위 특정 핸들러에 걸리지 않은 예기치 못한 예외의 공통 500 처리(부채 #22). 엔드포인트가 늘어도
     * 예상 밖 런타임 예외가 스프링 기본 응답 대신 팀 계약({@code code}, {@code message})으로 나가게 한다.
     * <p>
     * ★스프링 MVC/서블릿이 정의한 "요청 자체가 잘못된" 예외(잘못된 메서드 405·미지원 미디어타입 415/406·
     * 요청 바디 파싱 실패·바인딩 타입 불일치·필수 파라미터 누락 등)는 이미 올바른 4xx 상태로 처리된다.
     * 여기서 삼켜 500으로 바꾸면 회귀이므로(예: 인증 컨트롤러의 {@code @RequestBody} 바디 파싱 실패 400 → 500),
     * 원 예외를 그대로 다시 던져 스프링 기본 해석기에 위임한다 — 상태코드·바디 모두 기존과 동일.
     * <p>
     * 🔴 응답 바디에는 스택트레이스·예외 클래스명·메시지를 넣지 않는다(정보 노출 방지). 대신 서버 로그에
     * 전체 예외를 남겨 디버깅을 가능케 한다. 인증 401/403은 시큐리티 필터 단계에서 처리되어 여기 도달하지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) throws Exception {
        if (isSpringHandledRequestException(e)) {
            throw e;
        }
        log.error("처리되지 않은 서버 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."));
    }

    /**
     * 스프링/서블릿이 스스로 올바른 4xx로 처리하는 요청 예외인지 판별. 이 경우 catch-all이 삼키지 않고
     * 원 예외를 재던져 기존 처리(상태·바디)를 보존한다. {@code ErrorResponse}를 구현하지 않는 몇몇
     * 예외(바디 파싱·타입 불일치·바인딩)는 상위 타입으로 함께 걸러낸다.
     */
    private static boolean isSpringHandledRequestException(Exception e) {
        // ★org.springframework.web.ErrorResponse 는 이 클래스와 같은 패키지의 ErrorResponse 레코드와 단순명이
        //   겹쳐 임포트하면 레코드가 가려진다. 그래서 스프링 인터페이스는 완전수식명으로 참조한다.
        return e instanceof org.springframework.web.ErrorResponse
                || e instanceof HttpMessageConversionException
                || e instanceof TypeMismatchException
                || e instanceof BindException;
    }
}
