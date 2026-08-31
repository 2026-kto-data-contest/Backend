package com.jeontongjuro.backend.course;

/** 카카오 장소 검색에서 추천 코스 카드에 필요한 최소 정보. */
public record KakaoPlaceMatch(
        String placeId,
        String placeUrl,
        String categoryName
) {
}
