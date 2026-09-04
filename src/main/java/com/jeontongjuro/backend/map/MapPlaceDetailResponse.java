package com.jeontongjuro.backend.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 지도 장소 상세 조회 응답(단건). 소스는 요청 category에 따라 갈린다 —
 * {@code BREWERY}는 brewery 마스터, 그 외는 tour_content(TourAPI 캐시).
 * <p>
 * ★{@link MapPlaceResponse}(목록)와 필드 구성이 다르다. 목록은 {@code distance}(km)·
 * {@code roadAddressName}을 쓰고 상세는 {@code distanceMeters}(m)·{@code address}를 쓴다.
 * 또 목록의 {@code categoryName}에는 세부분류(예: "한식")가 들어가지만, 상세는 대분류 한글 라벨
 * (예: "음식점")을 {@code categoryName}에, 세부분류를 {@code subcategoryName}에 나눠 담는다 — 기능명세 확정.
 * <p>
 * ★{@code phone}은 tour_content에 전화번호 컬럼이 없어 양조장이 아닌 장소에서는 항상 {@code null}이다.
 * ★{@code distanceMeters}는 이번 명세에서 항상 {@code null}이다(기준 좌표를 받지 않는다).
 * 필드는 후속 additive 확장을 위해 계약에 미리 포함한다.
 * <p>
 * ★이미지 저작권: 목록·상세 모두 {@code imageUrl} 문자열 하나만 내려 공공누리 유형(Type1/Type3)을
 * 전달하지 못한다. 양조장 상세({@code brewery/query})는 {@code mainImage} 객체로 {@code modifiable}을 함께 준다.
 */
public record MapPlaceDetailResponse(
        @Schema(description = "장소 고유 ID. 양조장은 BRW-xxx, 그 외는 관광공사 content_id",
                example = "2788304", requiredMode = Schema.RequiredMode.REQUIRED)
        String placeId,

        @Schema(description = "장소명", example = "(주)외식명가 오립스",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String placeName,

        @Schema(description = "지도 목록과 동일한 5종 카테고리. 전통시장·문화시설은 TOURIST_ATTRACTION으로 합쳐진다",
                example = "RESTAURANT", requiredMode = Schema.RequiredMode.REQUIRED)
        MapPlaceCategory category,

        @Schema(description = "카테고리 한글 라벨(양조장·음식점·카페·숙소·관광지)",
                example = "음식점", requiredMode = Schema.RequiredMode.REQUIRED)
        String categoryName,

        @Schema(description = "사용자용 세부분류(예: 한식·카페·펜션·전통시장). 양조장이거나 세부분류가 없으면 null",
                example = "한식", nullable = true)
        String subcategoryName,

        @Schema(description = "위도", example = "35.267640", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,

        @Schema(description = "경도", example = "128.862336", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,

        @Schema(description = "기준 좌표에서의 거리(m). 이번 명세에서는 항상 null", nullable = true)
        Integer distanceMeters,

        @Schema(description = "주소. 양조장은 원문, 그 외는 addr1·addr2 조합",
                example = "경상남도 김해시 김해대로1765번길 14 (삼계동)", nullable = true)
        String address,

        // ★example을 두지 않는다 — 이 스키마의 다른 example은 모두 음식점 한 행(content_id=2788304)에서
        //   뽑았고 그 행의 phone은 null이다. 양조장 형식("033-340-4300")은 설명에만 적어 행 정합을 지킨다.
        @Schema(description = "전화번호(양조장 형식 예: 033-340-4300). 양조장만 값이 있고 "
                + "그 외는 항상 null — 원천 tour_content에 전화번호 컬럼이 없다", nullable = true)
        String phone,

        @Schema(description = "대표 이미지 URL. 없으면 null",
                example = "http://tong.visitkorea.or.kr/cms/resource/17/2788317_image2_1.JPG", nullable = true)
        String imageUrl,

        @Schema(description = "카카오맵 링크. 양조장은 저장된 장소 URL을 우선 사용하고, 없으면 이름·좌표로 길찾기 링크를 만든다",
                example = "https://map.kakao.com/link/to/%28%EC%A3%BC%29%EC%99%B8%EC%8B%9D%EB%AA%85%EA%B0%80"
                        + "+%EC%98%A4%EB%A6%BD%EC%8A%A4,35.267640,128.862336", nullable = true)
        String kakaoMapUrl) {
}
