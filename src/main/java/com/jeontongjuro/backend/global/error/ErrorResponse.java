package com.jeontongjuro.backend.global.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공통 에러 응답 바디. ★팀 합의 포맷 = {@code code}, {@code message} 딱 2필드.
 * timestamp·path 등은 넣지 않는다(클라이언트 계약 고정 — 필드 추가는 팀 합의 후).
 *
 * <p>{@code code}는 클라이언트 분기용 안정 식별자(문자열 상수), {@code message}는 사람이 읽는 설명이다.
 */
public record ErrorResponse(
        @Schema(description = "서비스에서 정의한 오류 코드", example = "AUTHENTICATION_REQUIRED") String code,
        @Schema(description = "사용자에게 전달할 오류 메시지", example = "로그인이 필요합니다.") String message
) {
}
