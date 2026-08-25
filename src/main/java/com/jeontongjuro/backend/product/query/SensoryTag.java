package com.jeontongjuro.backend.product.query;

/**
 * 표준 맛 태그 8종(기획 확정). 나열 순서 고정(응답 배열 순서 = 이 선언 순서) — 온보딩 취향 기반 정렬은
 * 취향 데이터가 백엔드에 없어 이번 범위 밖이다.
 */
public enum SensoryTag {
    상큼함,
    달콤함,
    드라이,
    산미,
    부드러움,
    묵직함,
    깔끔함,
    향긋함
}
