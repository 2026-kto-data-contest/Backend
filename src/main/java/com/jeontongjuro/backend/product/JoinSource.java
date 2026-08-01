package com.jeontongjuro.backend.product;

/**
 * product↔brewery 연결 경로(3c-2). MANUAL wins 순서: OVERRIDE_ROW/OVERRIDE_NAME이 AUTO를 덮어쓴다.
 * UNMATCHED는 이번 세션엔 적재하지 않는 값(스키마만 정의 — 1215 전체 확장 대비).
 */
public enum JoinSource {
    AUTO,
    OVERRIDE_NAME,
    OVERRIDE_ROW,
    UNMATCHED
}
