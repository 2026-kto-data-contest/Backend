package com.jeontongjuro.backend.tour;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * TourAPI(KorService2) 호출 경계. 파싱까지만 책임지고(무손실 문자열 {@link TourContentRow}), 좌표 검증·
 * 축 확정·대입은 산출/대입 계층 몫이다(geo의 KakaoGeocodingClient 분업과 동일).
 */
public interface TourApiClient {

    /**
     * locationBasedList2 — 좌표 반경 내 콘텐츠 전량(내부 페이징으로 전 페이지 병합). 결과 없으면 빈 리스트.
     * contentTypeId 미지정(전 타입 수신). 좌표축: mapX=경도, mapY=위도.
     *
     * @param latitude  반경 중심 위도(brewery.latitude)
     * @param longitude 반경 중심 경도(brewery.longitude)
     * @param radiusM   반경(m, tour.api.radius-m)
     */
    List<TourContentRow> locationBasedList(BigDecimal latitude, BigDecimal longitude, int radiusM);

    /**
     * detailCommon2 — 단건 상세(시드 접지용, 좌표·대표이미지 확보). 없으면 empty.
     * ★overview는 여기 담지 않는다 — 백필은 {@link #detailOverview}로 분리(회귀 봉인된 record 불변 유지).
     */
    Optional<TourContentRow> detailCommon(String contentId);

    /**
     * detailCommon2 — 소개글(overview) 단건(#50 백필용). detailCommon2 응답에서 overview만 뽑는다.
     * 결과 없음·overview 빈 문자열이면 empty(빈 문자열 저장 금지). contentTypeId 불필요(KorService2 v2).
     */
    Optional<String> detailOverview(String contentId);

    /**
     * detailIntro2 — 운영시간·휴무·전화·주차·수용인원 단건(#50 12단계). 없으면 empty. 빈 문자열은 null 정규화.
     * ★detailIntro2는 contentTypeId 필수(detailCommon2와 다름) — 콘텐츠 타입을 함께 넘긴다.
     *
     * @param contentId     TourAPI contentid
     * @param contentTypeId contenttypeid(양조장은 12) — 필수 파라미터
     */
    Optional<TourIntro> detailIntro(String contentId, String contentTypeId);
}
