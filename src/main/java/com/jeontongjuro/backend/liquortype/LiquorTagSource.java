package com.jeontongjuro.backend.liquortype;

/**
 * 주종 태깅 출처. {@code @Enumerated(EnumType.STRING)} → schema.sql의
 * {@code ck_product_liquor_type_source CHECK (... IN ('AUTO','MANUAL'))}와 대응.
 * <p>
 * MANUAL wins: 같은 (제품, 주종)에 MANUAL이 있으면 AUTO는 그 조합을 적재하지 않는다.
 * AUTO는 전건 recheck_flag=true(미검증), MANUAL은 recheck_flag=false(사람이 확정).
 */
public enum LiquorTagSource {
    AUTO,
    MANUAL
}
