package com.jeontongjuro.backend.feature;

/**
 * 양조장 특징 태그 5종(이슈 #43). 제품 서술 컬럼(product_raw)에서 규칙 파생해 양조장으로 롤업한다.
 * {@code @Enumerated(EnumType.STRING)}으로 저장하므로 상수명이 그대로 DB 값이 되고,
 * schema.sql의 {@code ck_brewery_feature_tag_type CHECK (... IN ('수상이력', ...))}와 1:1 대응한다.
 * <p>
 * ★확정 파생이다(주종과 달리 검수 워크플로·MANUAL 시드 없음) — 100% 규칙 산출.
 * 필터 파라미터는 이 PR 범위 밖 — 응답 노출(배지)만 한다. 따라서 {@code LiquorType.from}식
 * 조회 허용 집합·400 매핑을 두지 않는다(요청 밖 기능 미구현).
 */
public enum FeatureType {
    수상이력,
    식품명인,
    유기농,
    무형문화재,
    대통령상
}
