package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.SensoryTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 양조장 리스트 아이템 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션).
 * <p>
 * 노출 필드는 리스트 조회에 필요한 것만: 식별자·상호명·지역(sido/region)·방문 3-state·특징 태그.
 * visit 상태는 enum 그대로 직렬화되어 "Y"/"N"/"UNKNOWN" 문자열이 된다.
 * featureTags는 특징 배지 문자열 배열(예: ["수상이력","유기농"]) — 특징 없으면 빈 배열([]).
 * ★필터(?feature=)는 이 PR 범위 밖 — 데이터 노출(배지)만 한다.
 * <p>
 * ★리스트는 스캔용이라 주소·좌표·홈페이지는 넣지 않는다(그건 상세 응답 몫). 대신 카드에서 바로 쓰는
 * 도수(alcoholMin/alcoholMax)·주종(liquorTypes)·대표 이미지(mainImage)를 additive로 추가한다 —
 * 기존 7필드(breweryId~featureTags)의 이름·타입·순서는 그대로 유지한다(계약 불변).
 * <p>
 * 대표 이미지는 tour_content(TourAPI 캐시)의 first_image에서 온다({@link MainImageResponse}).
 * 대표 이미지가 없으면 mainImage=null. ★brewery.image_url 격리 컬럼(C-10)은 #56에서 제거됐다.
 * <p>
 * ★목록 응답 3차 additive(시군구·맛 태그·소개): 기존 11필드의 이름·타입·순서는 그대로 유지한다(계약 불변).
 * sigungu는 {@link com.jeontongjuro.backend.brewery.BrewerySigunguParser}(주소 파싱 파생값이라 nullable), flavorTags는
 * {@link com.jeontongjuro.backend.product.query.SensoryTagMatcher}가 대표 제품 1종의 '특징' 원문에서
 * 매칭한 결과(없으면 빈 배열 — '+N' 절단은 프론트 몫이라 매칭분을 전부 내린다), introduction은
 * {@link BreweryIntroductionResolver}의 우선순위 병합 결과(둘 다 없으면 null)다.
 */
public record BreweryListItemResponse(
        @Schema(description = "양조장 고유 ID", example = "BRW-001",
                requiredMode = Schema.RequiredMode.REQUIRED) String breweryId,
        @Schema(description = "화면에 표시할 양조장 이름", example = "해남 장독대 양조장",
                requiredMode = Schema.RequiredMode.REQUIRED) String businessName,
        // sido·region은 주소 파싱(applyRegion) 파생값이라 미계산 상태(신규 양조장 등)면 null이 될 수 있다
        // — 현재 59/59 채워져 있으나 그건 스냅샷일 뿐, 계약은 nullable이다.
        @Schema(description = "시·도 단위 주소. 주소 파싱 전이면 null", example = "전남", nullable = true) String sido,
        @Schema(description = "서비스 지역 필터 분류. 주소 파싱 전이면 null", example = "전라", nullable = true) String region,
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
        // ── 3차 additive(시군구·맛 태그·소개) ──────────────────────────────────────
        @Schema(description = "시군구 라벨(주소에서 시도 프리픽스를 벗기고 시/군/구 접미어를 제거한 값). "
                + "주소 파싱 파생값이라 코드 경로상 null 가능(현재 데이터는 59/59 non-null)",
                example = "안산", nullable = true) String sigungu,
        @Schema(description = "맛 태그(표준 8종 중 매칭분 전부, 선언 순서 고정). 대표 제품 1종의 '특징' 원문 "
                + "부분일치 매칭. 없으면 빈 배열. '+N' 절단은 프론트 몫",
                example = "[\"달콤함\",\"산미\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<SensoryTag> flavorTags,
        @Schema(description = "양조장 소개(관광공사 overview 우선, 없으면 농림부 designation_note). "
                + "둘 다 없으면 null. 말줄임은 프론트가 처리(백엔드는 자르지 않음)",
                example = "1918년 창업한 …", nullable = true) String introduction) {

    /** 태그·도수·주종·대표 이미지·시군구·맛 태그·소개는 서비스가 배치 조회해 주입한다(Brewery 엔티티만으로는 알 수 없다). */
    public static BreweryListItemResponse from(Brewery b, List<FeatureType> featureTags,
                                               BigDecimal alcoholMin, BigDecimal alcoholMax,
                                               List<LiquorType> liquorTypes, MainImageResponse mainImage,
                                               String sigungu, List<SensoryTag> flavorTags, String introduction) {
        return new BreweryListItemResponse(
                b.getBreweryId(),
                b.getBusinessName(),
                b.getSido(),
                b.getRegion(),
                b.getReservationVisitState(),
                b.getAlwaysVisitState(),
                featureTags,
                alcoholMin,
                alcoholMax,
                liquorTypes,
                mainImage,
                sigungu,
                flavorTags,
                introduction);
    }
}
