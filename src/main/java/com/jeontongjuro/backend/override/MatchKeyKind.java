package com.jeontongjuro.backend.override;

/**
 * match_key의 종류.
 * <ul>
 *   <li>{@code BREWERY_NORM} — match_key가 product 양조장명의 norm(NAME_MAP과 짝).</li>
 *   <li>{@code PRODUCT_NAME} — match_key가 제품명(ROW_PIN과 짝).</li>
 * </ul>
 */
public enum MatchKeyKind {
    BREWERY_NORM,
    PRODUCT_NAME
}
