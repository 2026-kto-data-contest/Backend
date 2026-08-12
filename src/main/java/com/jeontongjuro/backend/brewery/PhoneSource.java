package com.jeontongjuro.backend.brewery;

/**
 * 전화번호 출처(#50). 같은 양조장에 관광공사·카카오 두 소스가 겹칠 수 있어(교집합 18곳, 값 상이 5곳)
 * "이 phone이 어디서 왔는가"를 기록한다 — 나중에 값이 틀렸을 때 판단 근거.
 * <p>
 * 우선순위 {@code TOUR > KAKAO}: 관광공사 infocenter는 담당자 등록 대표번호이고, 카카오는 상호검색+
 * 200m 좌표 게이트 스크랩이라 매칭 자체에 오차 여지가 있다. 그래서 12단계(관광공사)가 먼저 채우고
 * 13단계(카카오)는 비어 있는 곳만 보충한다. schema.sql {@code ck_brewery_phone_source} CHECK와 1:1 대응.
 */
public enum PhoneSource {
    /** 관광공사 detailIntro2 infocenter(콘텐츠 매칭 19곳). 우선 소스. */
    TOUR,
    /** 카카오 로컬 검색 시드(관광공사에 없는 곳 보충). */
    KAKAO
}
