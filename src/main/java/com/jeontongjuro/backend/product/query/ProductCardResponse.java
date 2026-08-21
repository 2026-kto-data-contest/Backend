package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 양조장 상세 화면의 제품 카드 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션).
 * <p>
 * 한 카드 = 판매중단·원본오류 제외 및 중복 병합을 거친 노출 제품 1건. 병합된 카드의 스칼라(도수·용량)는
 * 대표 행에서 통째로 가져오고(필드별로 섞지 않는다 — 존재하지 않는 SKU 방지), 텍스트는 필드별로 병합한다.
 * <p>
 * 결측 규약: 배열({@code liquorTypes})은 없으면 빈 배열, 스칼라·문자열은 없으면 null.
 * <ul>
 *   <li>{@code alcoholMin/Max} — 도수 정보가 없으면 null. 단일 도수 제품은 min==max.</li>
 *   <li>{@code volume} — 원문 그대로(파싱·정규화 안 함). 복수 표기도 원문 유지. 없으면 null.</li>
 *   <li>{@code description} — 절단 되돌림/미노출 처리 후. 노출할 문장이 없으면 null.</li>
 *   <li>{@code awardBadge} — 최상위 등급 라벨 또는 {@code "수상"}. 수상 이력이 없으면 null.</li>
 * </ul>
 */
public record ProductCardResponse(
        @Schema(description = "제품 식별자(병합 후 대표 행의 source_row_ref)", example = "441",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer productId,
        @Schema(description = "화면에 표시할 제품명(병합 대표명, 원문)", example = "진맥 소주",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String productName,
        @Schema(description = "최소 도수(%). 도수 정보가 없으면 null. 단일 도수면 최대와 같음", example = "40.0",
                nullable = true)
        BigDecimal alcoholMin,
        @Schema(description = "최대 도수(%). 도수 정보가 없으면 null", example = "40.0", nullable = true)
        BigDecimal alcoholMax,
        @Schema(description = "용량 원문(파싱하지 않음, 복수 표기 가능). 없으면 null", example = "200ml",
                nullable = true)
        String volume,
        // ★목록·상세와 타입 통일: List<LiquorType>(enum). 값 문자열은 enum명과 1:1이라 wire JSON은 불변(["증류주"]),
        //   OpenAPI엔 주종 enum 값 목록이 드러나 프론트 코드젠이 문자열 대신 타입을 얻는다.
        @Schema(description = "취급 주종 목록(탁주·약주·청주·증류주·과실주·기타). 없으면 빈 배열",
                example = "[\"증류주\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LiquorType> liquorTypes,
        @Schema(description = "제품 소개(절단 되돌림/미노출 처리 후). 노출할 문장이 없으면 null", nullable = true)
        String description,
        @Schema(description = "최상위 수상 등급 뱃지 또는 '수상'. 수상 이력이 없으면 null", example = "대상",
                nullable = true)
        String awardBadge) {
}
