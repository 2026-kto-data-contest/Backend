package com.jeontongjuro.backend.override;

/**
 * 수기 보정 매칭 방식(폴리모픽).
 * <ul>
 *   <li>{@code NAME_MAP} — product 양조장명의 norm으로 매칭(양조장명 필드가 존재하나 자동 정규화로는 미해소).</li>
 *   <li>{@code ROW_PIN} — 원본 양조장 필드가 null이라 양조장명 매칭 불가 → 제품명으로 특정 행을 고정.</li>
 * </ul>
 */
public enum OverrideType {
    NAME_MAP,
    ROW_PIN
}
