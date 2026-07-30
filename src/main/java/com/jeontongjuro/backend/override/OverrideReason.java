package com.jeontongjuro.backend.override;

/**
 * 보정 근거. assignment §결정4 확정값.
 * <ul>
 *   <li>{@code ADDR_EXACT} — 주소 완전 일치로 동일 양조장 확정.</li>
 *   <li>{@code ADDR_STRONG} — 주소 강한 근거(부분/정황)로 확정.</li>
 *   <li>{@code MANUAL_DOMAIN} — 도메인 지식 기반 수기 확정(원본 양조장 필드 결손). recheck 대상.</li>
 * </ul>
 */
public enum OverrideReason {
    ADDR_EXACT,
    ADDR_STRONG,
    MANUAL_DOMAIN
}
