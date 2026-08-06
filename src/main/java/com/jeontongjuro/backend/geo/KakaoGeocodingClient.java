package com.jeontongjuro.backend.geo;

import java.util.Optional;

/**
 * 카카오 Local API 주소 검색 클라이언트(호출·응답 파싱만 — 폴백 체인·범위 검증은 {@link GeocodingService}).
 * <p>
 * 인터페이스로 분리하는 이유: 테스트가 카카오를 실제 호출하면 안 되므로, 골든 검증 테스트는 스텁 구현으로
 * 대체한다(운영 구현 {@link KakaoGeocodingClientImpl}).
 */
public interface KakaoGeocodingClient {

    /**
     * 주소로 좌표를 조회한다. 무결과({@code meta.total_count == 0})면 {@link Optional#empty()} —
     * 「응답이 왔다」와 「값이 맞다」는 다르므로 무결과는 폴백 신호이지 실패가 아니다.
     * HTTP 오류(키 오류 등)는 예외로 전파한다(삼키지 않음).
     *
     * @param query 조회 주소 문자열
     * @return 채택된 {@code documents[0]} 매치, 무결과면 empty
     */
    Optional<KakaoAddressMatch> geocode(String query);
}
