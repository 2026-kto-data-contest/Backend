package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.PhoneSource;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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
        @Schema(description = "양조장 고유 ID", example = "BRW-001",
                requiredMode = Schema.RequiredMode.REQUIRED) String breweryId,
        @Schema(description = "화면에 표시할 양조장 이름", example = "해남 장독대 양조장",
                requiredMode = Schema.RequiredMode.REQUIRED) String businessName,
        // sido·region은 주소 파싱(applyRegion) 파생값이라 미계산 상태(신규 양조장 등)면 null이 될 수 있다(계약 nullable).
        @Schema(description = "시·도 단위 주소. 주소 파싱 전이면 null", example = "전라남도", nullable = true) String sido,
        @Schema(description = "서비스 지역 필터 분류. 주소 파싱 전이면 null", example = "전라", nullable = true) String region,
        @Schema(description = "도로명/지번 주소 원문", example = "전라남도 해남군 ...",
                requiredMode = Schema.RequiredMode.REQUIRED) String address,
        @Schema(description = "위도(WGS84). 지오코딩 실패 시 null", example = "34.573933", nullable = true)
        BigDecimal latitude,
        @Schema(description = "경도(WGS84). 지오코딩 실패 시 null", example = "126.598912", nullable = true)
        BigDecimal longitude,
        @Schema(description = "홈페이지 URL. 없으면 null. 스킴 없는 원문은 http://를 붙여 내린다",
                example = "http://example.co.kr", nullable = true) String homepageUrl,
        @Schema(description = "예약 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "Y",
                requiredMode = Schema.RequiredMode.REQUIRED)
        VisitState reservationVisitState,
        @Schema(description = "상시 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "UNKNOWN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        VisitState alwaysVisitState,
        @Schema(description = "특징 태그 배지(수상이력·식품명인·유기농·무형문화재·대통령상). 없으면 빈 배열",
                example = "[\"수상이력\",\"유기농\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FeatureType> featureTags,
        @Schema(description = "취급 제품 최소 도수(%). 도수 정보가 있는 제품이 없으면 null", example = "6.0",
                nullable = true)
        BigDecimal alcoholMin,
        @Schema(description = "취급 제품 최대 도수(%). 도수 정보가 있는 제품이 없으면 null", example = "40.0",
                nullable = true)
        BigDecimal alcoholMax,
        @Schema(description = "취급 주종 목록(탁주·약주·청주·증류주·과실주·기타). 없으면 빈 배열",
                example = "[\"탁주\",\"증류주\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LiquorType> liquorTypes,
        @Schema(description = "대표 이미지. 없으면 null", nullable = true) MainImageResponse mainImage,
        // ── 상세 필드 편입(#50, additive). 값 없으면 전부 null. 리스트 API엔 노출하지 않는다(스캔용). ──
        @Schema(description = "양조장 소개글(관광공사). 콘텐츠 미매칭이면 null", example = "1918년 창업한 …",
                nullable = true)
        String overview,
        @Schema(description = "대표 전화. 없으면 null", example = "041-363-9063", nullable = true) String phone,
        @Schema(description = "전화 출처: TOUR(관광공사 우선)·KAKAO(보충). phone 없으면 null", example = "TOUR",
                nullable = true)
        PhoneSource phoneSource,
        @Schema(description = "운영시간 원문(정형화 불가·개행 보존). 없으면 null", example = "09:00~18:00",
                nullable = true)
        String operatingHours,
        @Schema(description = "휴무 원문. 없으면 null", example = "매주 일요일", nullable = true) String restDate,
        @Schema(description = "주차 안내. 없으면 null", example = "가능", nullable = true) String parkingInfo,
        @Schema(description = "수용인원 원문(숫자 아님). 없으면 null", example = "최대 80명", nullable = true)
        String accomCount,
        @Schema(description = "설립연도(농림부 2019). 없으면 null", example = "1933", nullable = true)
        Integer foundedYear,
        @Schema(description = "대표자명(농림부 2019). 없으면 null", example = "김동교", nullable = true)
        String representativeName,
        @Schema(description = "찾아가는 양조장 선정연도(농림부 2019). 없으면 null", example = "2013", nullable = true)
        Integer designatedYear,
        @Schema(description = "농림부 특징 서술(2019). 없으면 null", example = "80년 전통 3대째 양조장 …",
                nullable = true)
        String designationNote,
        // ── 체험 프로그램 편입(#52, additive). 체험 없으면 빈 배열. 리스트 API엔 노출하지 않는다(스캔용). ──
        @Schema(description = "aT 전통주 체험 프로그램 목록(program_name 오름차순). 없으면 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExperienceResponse> experiences,
        // ── 카카오 place URL 편입(#54, additive). 없으면 null. 리스트 API엔 노출하지 않는다(스캔용). ──
        @Schema(description = "카카오 place 딥링크 URL(지도·상세). 없으면 null. http:// 원문(접속 시 https 리다이렉트)",
                example = "http://place.map.kakao.com/17112140", nullable = true)
        String kakaoPlaceUrl) {

    /**
     * 엔티티 + 배치 조회로 모은 파생값(태그·도수·주종·대표 이미지·소개글)을 합쳐 상세 응답을 만든다.
     * overview는 tour_content에서 오므로 별도 인자로 받는다(엔티티 밖 값). 나머지 상세 필드는 brewery 엔티티에 있다.
     */
    public static BreweryDetailResponse of(Brewery b, List<FeatureType> featureTags,
                                           BigDecimal alcoholMin, BigDecimal alcoholMax,
                                           List<LiquorType> liquorTypes, MainImageResponse mainImage,
                                           String overview, List<ExperienceResponse> experiences) {
        return new BreweryDetailResponse(
                b.getBreweryId(),
                b.getBusinessName(),
                b.getSido(),
                b.getRegion(),
                b.getAddress(),
                b.getLatitude(),
                b.getLongitude(),
                withHttpScheme(b.getHomepageUrl()),
                b.getReservationVisitState(),
                b.getAlwaysVisitState(),
                featureTags,
                alcoholMin,
                alcoholMax,
                liquorTypes,
                mainImage,
                overview,
                b.getPhone(),
                b.getPhoneSource(),
                b.getOperatingHours(),
                b.getRestDate(),
                b.getParkingInfo(),
                b.getAccomCount(),
                b.getFoundedYear(),
                b.getRepresentativeName(),
                b.getDesignatedYear(),
                b.getDesignationNote(),
                experiences,
                b.getKakaoPlaceUrl());
    }

    /**
     * 홈페이지 URL 스킴 보정(응답 레벨 파생 — {@link MainImageResponse#from}의 modifiable 파생과 같은 사상:
     * 저작권/링크 판정을 프론트에 떠넘기지 않는다). raw 원문은 스킴 없는 도메인({@code www.…})일 수 있어
     * 프론트가 {@code <a href>}로 걸면 상대경로로 깨진다. 스킴이 없으면 {@code http://}를 붙여 내린다.
     * <p>
     * ★DB 컬럼(homepage_url)은 고치지 않는다 — 주소·홈페이지 raw 원문 보존 원칙(RegionParser가 raw address로
     * 지역을 산출한다). https가 아니라 http인 이유: 대상 도메인의 https 지원을 알 수 없어, kakao_place_url 선례처럼
     * http 원문으로 내리고 호스트 리다이렉트에 맡긴다. 이미 http/https면 원문 그대로, null이면 null.
     */
    static String withHttpScheme(String homepageUrl) {
        if (homepageUrl == null) {
            return null;
        }
        String lower = homepageUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return homepageUrl;
        }
        return "http://" + homepageUrl;
    }
}
