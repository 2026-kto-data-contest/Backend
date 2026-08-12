package com.jeontongjuro.backend.tour;

/**
 * TourAPI detailIntro2 단건 파싱 결과(#50 12단계). 무손실 문자열, 빈 문자열은 null로 정규화.
 * <p>
 * ★{@link TourContentRow}에 합치지 않는다 — 그 record는 골든 회귀에 봉인돼 있고 detailCommon2 필드셋이라,
 * intro 전용 필드(운영시간·휴무·전화·주차·수용인원)를 섞으면 무관한 테스트가 깨진다. 별도 record로 격리한다.
 * <p>
 * {@code operatingHours}는 원문 그대로가 아니라 {@code <br>} 태그를 개행으로 정규화한 값이다(데이터 필드에
 * HTML 저장 금지 — 프론트는 개행만 필요). 나머지는 원문. {@code phone}은 infocenter다.
 */
public record TourIntro(
        String contentId,
        String operatingHours,
        String restDate,
        String phone,
        String parkingInfo,
        String accomCount) {
}
