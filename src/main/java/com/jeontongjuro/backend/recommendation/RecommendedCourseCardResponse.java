package com.jeontongjuro.backend.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

/** 추천 코스 전체 목록과 홈 미리보기가 공유하는 카드 계약. */
public record RecommendedCourseCardResponse(
        @Schema(description = "코스 ID. 상세 조회의 breweryId로 그대로 사용", example = "BRW-001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String courseId,
        @Schema(description = "코스 대표 이미지 URL. 양조장 대표 이미지가 없으면 null", nullable = true)
        String imageUrl,
        @Schema(description = "시도·시군구 지역 라벨", example = "충북 영동", nullable = true)
        String regionLabel,
        @Schema(description = "코스명", example = "갈기산 양조장 코스",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title
) {
}
