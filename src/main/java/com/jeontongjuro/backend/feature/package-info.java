/**
 * Note: 양조장 특징 도메인(이슈 #43) — 제품 서술 컬럼(product_raw)에서 특징 5종(수상이력·식품명인·유기농·
 * 무형문화재·대통령상)을 규칙 파생해 `brewery_feature_tag`(양조장 grain)로 롤업한다. 확정 파생(검수 없음)이라
 * 삭제형 diff로 유령 행을 남기지 않는다. 필터는 범위 밖 — 조회 응답 배지 노출만.
 */
package com.jeontongjuro.backend.feature;
