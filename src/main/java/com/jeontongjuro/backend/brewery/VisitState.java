package com.jeontongjuro.backend.brewery;

/**
 * 방문 가능 여부 3-state. boolean 금지 근거: 원본에 null이 상당수 존재하므로
 * (예약방문 null 31/59, 상시방문 null 9/59) "미입력(UNKNOWN)"을 "불가(N)"와 구분해야 한다.
 * 변환 규칙: 원본 "Y"→Y, "N"→N, null(또는 그 외)→UNKNOWN.
 */
public enum VisitState {
    Y,
    N,
    UNKNOWN;

    /** 골든 raw의 방문여부 원문(Y/N/null)을 3-state로 변환. */
    public static VisitState fromRaw(String rawValue) {
        if ("Y".equals(rawValue)) {
            return Y;
        }
        if ("N".equals(rawValue)) {
            return N;
        }
        // null·공백·그 외 값은 전부 미입력으로 취급(원본 결측 보존)
        return UNKNOWN;
    }
}
