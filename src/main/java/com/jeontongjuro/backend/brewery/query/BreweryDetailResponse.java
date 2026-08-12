package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 양조장 상세 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션). GET /api/v1/breweries/{breweryId}.
 * <p>
 * 리스트(스캔용)와 달리 상세는 지도·연락 정보까지 전부 노출한다: 주소·좌표(latitude/longitude)·홈페이지.
 * 도수(alcoholMin/alcoholMax)·주종(liquorTypes)·특징 태그·대표 이미지는 배치 조회로 주입한다(엔티티만으론 불가).
 * <p>
 * 도수는 이 양조장 제품(product_brewery_link)의 alcohol_min 최솟값·alcohol_max 최댓값이다.
 * 도수 정보가 있는 제품이 하나도 없으면 둘 다 {@code null}(현재 전 양조장이 도수를 가지므로 실데이터엔 없음).
 * liquorTypes는 검수 상태(recheck_flag) 무관 전체 태깅의 distinct 집합이라 주종 필터(?liquorType=) 결과와 일치한다.
 */
public record BreweryDetailResponse(
        @Schema(description = "양조장 고유 ID", example = "BRW-001") String breweryId,
        @Schema(description = "화면에 표시할 양조장 이름", example = "해남 장독대 양조장") String businessName,
        @Schema(description = "시·도 단위 주소", example = "전라남도") String sido,
        @Schema(description = "서비스 지역 필터 분류", example = "전라") String region,
        @Schema(description = "도로명/지번 주소 원문", example = "전라남도 해남군 ...") String address,
        @Schema(description = "위도(WGS84). 지오코딩 실패 시 null", example = "34.573933") BigDecimal latitude,
        @Schema(description = "경도(WGS84). 지오코딩 실패 시 null", example = "126.598912") BigDecimal longitude,
        @Schema(description = "홈페이지 URL. 없으면 null", example = "http://example.co.kr") String homepageUrl,
        @Schema(description = "예약 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "Y")
        VisitState reservationVisitState,
        @Schema(description = "상시 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "UNKNOWN")
        VisitState alwaysVisitState,
        @Schema(description = "특징 태그 배지(수상이력·식품명인·유기농·무형문화재·대통령상). 없으면 빈 배열",
                example = "[\"수상이력\",\"유기농\"]")
        List<FeatureType> featureTags,
        @Schema(description = "취급 제품 최소 도수(%). 도수 정보가 있는 제품이 없으면 null", example = "6.0")
        BigDecimal alcoholMin,
        @Schema(description = "취급 제품 최대 도수(%). 도수 정보가 있는 제품이 없으면 null", example = "40.0")
        BigDecimal alcoholMax,
        @Schema(description = "취급 주종 목록(탁주·약주·청주·증류주·과실주·기타). 없으면 빈 배열",
                example = "[\"탁주\",\"증류주\"]")
        List<LiquorType> liquorTypes,
        @Schema(description = "대표 이미지. 없으면 null") MainImageResponse mainImage) {

    /** 엔티티 + 배치 조회로 모은 파생값(태그·도수·주종·대표 이미지)을 합쳐 상세 응답을 만든다. */
    public static BreweryDetailResponse of(Brewery b, List<FeatureType> featureTags,
                                           BigDecimal alcoholMin, BigDecimal alcoholMax,
                                           List<LiquorType> liquorTypes, MainImageResponse mainImage) {
        return new BreweryDetailResponse(
                b.getBreweryId(),
                b.getBusinessName(),
                b.getSido(),
                b.getRegion(),
                b.getAddress(),
                b.getLatitude(),
                b.getLongitude(),
                b.getHomepageUrl(),
                b.getReservationVisitState(),
                b.getAlwaysVisitState(),
                featureTags,
                alcoholMin,
                alcoholMax,
                liquorTypes,
                mainImage);
    }
}
