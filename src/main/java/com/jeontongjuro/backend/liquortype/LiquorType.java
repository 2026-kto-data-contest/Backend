package com.jeontongjuro.backend.liquortype;

/**
 * 주종 6종. {@code @Enumerated(EnumType.STRING)}으로 저장하므로 상수명이 그대로 DB 값이 되고,
 * schema.sql의 {@code ck_product_liquor_type_type CHECK (... IN ('탁주',...))}와 1:1 대응한다.
 * (한글 enum 상수명은 CHECK 제약의 한글 값과 매핑을 어긋남 없이 맞추기 위한 의도적 선택 — 별도 컨버터 불필요.)
 * <p>
 * ★{@link #기타}는 AUTO 추론이 만들지 않는다 — "어느 키워드에도 안 걸림(판정 안 됨)"과 "기타로 판정함"은 다르다.
 * 기타는 사람 판정(2차 MANUAL)만 부여한다. 여기선 enum 값으로 정의만 해 둔다.
 */
public enum LiquorType {
    탁주,
    약주,
    청주,
    증류주,
    과실주,
    기타
}
