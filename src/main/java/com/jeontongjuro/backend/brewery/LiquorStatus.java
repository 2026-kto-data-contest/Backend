package com.jeontongjuro.backend.brewery;

/**
 * 주종 태깅 상태 3-state(UNJOINED이면 NA). ★이번(3c-1)엔 계산하지 않는다 — 전 행 초기값 NA로 적재하고,
 * 후속 주종 롤업 단계(3d)가 실제 태깅 결과로 UPDATE한다.
 */
public enum LiquorStatus {
    TAGGED,
    UNTAGGED,
    NA
}
