package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.feature.FeatureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 양조장 리스트 아이템 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션).
 * <p>
 * 노출 필드는 리스트 조회에 필요한 것만: 식별자·상호명·지역(sido/region)·방문 3-state·특징 태그.
 * visit 상태는 enum 그대로 직렬화되어 "Y"/"N"/"UNKNOWN" 문자열이 된다.
 * featureTags는 특징 배지 문자열 배열(예: ["수상이력","유기농"]) — 특징 없으면 빈 배열([]).
 * ★필터(?feature=)는 이 PR 범위 밖 — 데이터 노출(배지)만 한다.
 * <p>
 * image_url은 이번에 제외한다 — 소스 미확정(C-10)으로 전행 null인 격리 컬럼이라,
 * 값이 확정되는 후속 PR에서 필드를 추가(additive)하는 편이 계약을 깔끔하게 유지한다.
 */
public record BreweryListItemResponse(
        @Schema(description = "양조장 고유 ID", example = "BRW-001") String breweryId,
        @Schema(description = "화면에 표시할 양조장 이름", example = "해남 장독대 양조장") String businessName,
        @Schema(description = "시·도 단위 주소", example = "전라남도") String sido,
        @Schema(description = "서비스 지역 필터 분류", example = "전라") String region,
        @Schema(description = "예약 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "Y")
        VisitState reservationVisitState,
        @Schema(description = "상시 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "UNKNOWN")
        VisitState alwaysVisitState,
        @Schema(description = "특징 태그 배지(수상이력·식품명인·유기농·무형문화재·대통령상). 없으면 빈 배열",
                example = "[\"수상이력\",\"유기농\"]")
        List<FeatureType> featureTags) {

    /** 태그는 서비스가 배치 조회해 주입한다(Brewery 엔티티만으로는 특징을 알 수 없다). */
    public static BreweryListItemResponse from(Brewery b, List<FeatureType> featureTags) {
        return new BreweryListItemResponse(
                b.getBreweryId(),
                b.getBusinessName(),
                b.getSido(),
                b.getRegion(),
                b.getReservationVisitState(),
                b.getAlwaysVisitState(),
                featureTags);
    }
}
