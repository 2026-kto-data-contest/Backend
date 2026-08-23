package com.jeontongjuro.backend.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 추천 코스 도메인 구현 전 홈 응답 계약을 고정하기 위한 카드 DTO. */
public record HomeCourseCardResponse(
        @Schema(description = "추천 코스 ID") String courseId,
        @Schema(description = "코스 배너 이미지 URL") String imageUrl,
        @Schema(description = "코스 지역 라벨") String regionLabel,
        @Schema(description = "코스명") String title
) {
}
