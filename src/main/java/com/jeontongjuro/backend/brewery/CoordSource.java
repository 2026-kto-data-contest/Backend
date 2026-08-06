package com.jeontongjuro.backend.brewery;

/**
 * 좌표 출처(#28 8단계 지오코딩 폴백 단계 식별). 좌표는 언제나 카카오가 계산하며, 이 값은 "어떤 주소로
 * 조회해 성공했는가"만 구분한다.
 * <p>
 * ★컬럼 생성 ≠ 값 확정: 실사용 3값만 둔다. {@code KAKAO_KEYWORD}·{@code MANUAL}은 실사용 0건이라
 * 만들지 않는다(필요 시 additive). schema.sql {@code ck_brewery_coord_source} CHECK와 1:1 대응.
 */
public enum CoordSource {
    /** raw address 그대로 조회 성공. */
    KAKAO_ADDRESS,
    /** raw 실패 후 정규화 규칙(쉼표 절단·부번지 폴백) 적용해 조회 성공. */
    KAKAO_ADDRESS_NORMALIZED,
    /** address_fix_seed의 사람 보정 주소로 조회 성공(raw는 카카오 DB 부재·오류). */
    KAKAO_ADDRESS_SEED
}
