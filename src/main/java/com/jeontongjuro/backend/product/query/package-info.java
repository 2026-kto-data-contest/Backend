/**
 * 양조장 제품 목록 조회 계층(GET /api/v1/breweries/{breweryId}/products).
 * <p>
 * {@code brewery/query} 패키지 구조를 미러링한다 — 컨트롤러 + 서비스(명시 매핑) + 응답 DTO. 제품 카드는
 * {@code product_brewery_link}(도수·조인)와 {@code product_raw}(제품명·용량·소개·수상·판매여부)를 조인하고,
 * 판매중단·원본오류 제외 후 중복 병합해 만든다. 소개 절단 되돌림({@link com.jeontongjuro.backend.product.query.DescriptionTruncationPolicy})과
 * 수상 등급({@link com.jeontongjuro.backend.product.query.AwardGradeParser})은 순수 함수로 분리해 골든 픽스처로 검증한다.
 * <p>
 * ★DB 스키마 변경 0 — 원본오류 제외는 {@code product_exclusion_seed.json} 시드 파일을 조회 시점에 읽어 처리한다.
 */
package com.jeontongjuro.backend.product.query;
