package com.jeontongjuro.backend.brewery.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 양조장 대표 이미지 응답 DTO(리스트·상세 공용). 소스 = tour_content(TourAPI 캐시)의 first_image·cpyrht_div_cd.
 * <p>
 * ★대표 1장만 노출한다 — 추가 사진(105장)·배열화는 이번 범위 밖(후속 additive).
 * 대표 이미지가 없으면(콘텐츠 미매칭 또는 first_image 공백) 이 객체 자체를 {@code null}로 응답한다
 * (빈 문자열 URL을 흘리지 않는다 — {@link #from} 참조).
 * <p>
 * {@code modifiable}은 공공누리(KOGL) 저작권 유형을 프론트가 매핑하지 않도록 백엔드가 확정해 내리는 파생값이다:
 * cpyrht_div_cd가 {@code Type1}(제1유형, 출처표시)이면 변형 가능 → true, {@code Type3}(제3유형, 변경금지)이면 false.
 * ★그 외 값(미래 유형·공백 등)은 보수적으로 false로 내린다(허용을 넓히면 저작권 위반 위험이 크기 때문).
 *
 * @param url        대표 이미지 URL(first_image 원문)
 * @param copyright  저작권 구분 코드 원문(cpyrht_div_cd — 예: "Type1", "Type3")
 * @param modifiable 이미지 변형 허용 여부(Type1만 true, 그 외 전부 false)
 */
public record MainImageResponse(
        // 이 객체 자체가 null이거나(대표 이미지 없음), 존재하면 url은 반드시 non-blank(from에서 blank면 객체를 안 만든다).
        @Schema(description = "대표 이미지 URL", example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(description = "저작권 구분 코드(공공누리 유형): Type1(제1유형), Type3(제3유형). 원문 없으면 null",
                example = "Type1", nullable = true)
        String copyright,
        @Schema(description = "이미지 변형 허용 여부. Type1이면 true, 그 외(Type3 등)는 false", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean modifiable) {

    /** 공공누리 제1유형(출처표시) — 변형 허용. 이 값에서만 modifiable=true. */
    private static final String TYPE_MODIFIABLE = "Type1";

    /**
     * 대표 이미지 응답 생성. first_image가 null이거나 공백이면 {@code null}(대표 이미지 없음).
     * modifiable은 cpyrht_div_cd가 {@code Type1}일 때만 true, 그 외(Type3·미래 유형·null)는 보수적으로 false.
     */
    public static MainImageResponse from(String firstImage, String cpyrhtDivCd) {
        if (firstImage == null || firstImage.isBlank()) {
            return null;
        }
        return new MainImageResponse(firstImage, cpyrhtDivCd, TYPE_MODIFIABLE.equals(cpyrhtDivCd));
    }
}
