package com.jeontongjuro.backend.product.query;

/**
 * 검색 자동완성용 제품 이름 프로젝션(병합 후 대표 행 기준). {@link ProductQueryService#allDisplayedProductNames}가
 * 반환한다.
 */
public record ProductNameSuggestion(Integer productId, String productName) {
}
