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
     * ★overview도 응답에 오지만 이 record는 담지 않는다(이번 사이클 미충전).
     */
    Optional<TourContentRow> detailCommon(String contentId);
}
