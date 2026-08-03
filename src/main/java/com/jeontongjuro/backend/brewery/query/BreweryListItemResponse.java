package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.VisitState;

/**
 * 양조장 리스트 아이템 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션).
 * <p>
 * 노출 필드는 리스트 조회에 필요한 것만: 식별자·상호명·지역(sido/region)·방문 3-state.
 * visit 상태는 enum 그대로 직렬화되어 "Y"/"N"/"UNKNOWN" 문자열이 된다.
 * <p>
 * image_url은 이번에 제외한다 — 소스 미확정(C-10)으로 전행 null인 격리 컬럼이라,
 * 값이 확정되는 후속 PR에서 필드를 추가(additive)하는 편이 계약을 깔끔하게 유지한다.
 */
public record BreweryListItemResponse(
        String breweryId,
        String businessName,
        String sido,
        String region,
        VisitState reservationVisitState,
        VisitState alwaysVisitState) {

    public static BreweryListItemResponse from(Brewery b) {
        return new BreweryListItemResponse(
                b.getBreweryId(),
                b.getBusinessName(),
                b.getSido(),
                b.getRegion(),
                b.getReservationVisitState(),
                b.getAlwaysVisitState());
    }
}
