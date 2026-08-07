package com.jeontongjuro.backend.tour;

/**
 * TourAPI 응답 한 행의 무손실 파싱 결과(값 변환 없음, 원문 문자열 그대로). locationBasedList2(목록)와
 * detailCommon2(단건)가 같은 필드 집합을 돌려주므로 한 record로 공유한다 — 차이는 {@code distanceM}뿐이다
 * (목록응답만 반경 중심으로부터의 거리 {@code dist}를 주고, 단건 상세는 주지 않아 null).
 * <p>
 * ★좌표는 원문 문자열({@code mapx}=경도, {@code mapy}=위도)로 들고 있고, 범위 검증·축 확정·scale은
 * 산출 계층({@link TourGeoValidator})이 한다 — 클라이언트는 파싱만(geo의 KakaoAddressMatch/GeocodingService
 * 분업과 동일). overview는 담지 않는다(이번 사이클 미충전 — tour_content 주석 참조).
 */
public record TourContentRow(
        String contentId,
        String contentTypeId,
        String title,
        String addr1,
        String addr2,
        String zipcode,
        String areaCode,
        String sigunguCode,
        String cat1,
        String cat2,
        String cat3,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String lDongRegnCd,
        String lDongSignguCd,
        String mapx,
        String mapy,
        String mlevel,
        String firstImage,
        String firstImage2,
        String cpyrhtDivCd,
        String createdTime,
        String modifiedTime,
        Double distanceM
) {
}
