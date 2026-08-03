package com.jeontongjuro.backend.global.error;

/**
 * 공통 에러 응답 바디. ★팀 합의 포맷 = {@code code}, {@code message} 딱 2필드.
 * timestamp·path 등은 넣지 않는다(클라이언트 계약 고정 — 필드 추가는 팀 합의 후).
 *
 * <p>{@code code}는 클라이언트 분기용 안정 식별자(문자열 상수), {@code message}는 사람이 읽는 설명이다.
 */
public record ErrorResponse(String code, String message) {
}
