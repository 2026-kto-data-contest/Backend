package com.jeontongjuro.backend.brewery;

/**
 * 제품 조인 성립 여부 2-state. ★이번(3c-1)엔 계산하지 않는다 — 전 행 초기값 UNJOINED로 적재하고,
 * 후속 조인 단계(3c-2)가 실제 조인 결과로 UPDATE한다.
 */
public enum JoinStatus {
    JOINED,
    UNJOINED
}
