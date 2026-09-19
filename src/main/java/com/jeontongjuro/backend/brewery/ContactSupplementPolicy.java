package com.jeontongjuro.backend.brewery;

/**
 * 자동 수집이 놓친 연락처를 응답에서만 보충하는 정책. 대상은 BRW-051(청산녹수) 한 곳이다.
 * <p>
 * ★2026-09-19 카카오 로컬 키워드검색으로 직접 확인한 값이다. 우리 좌표 기준 반경 20km로 넓혀 조회했고
 * {@code total_count}는 1이었다 — 동명 후보가 없어 매칭이 유일하다. 값의 출처가 카카오이므로
 * {@link PhoneSource#KAKAO}를 그대로 쓴다. 수동인 것은 수집 방식이지 값의 출처가 아니다.
 * <p>
 * ★자동 수집에서 탈락한 이유: 매칭된 장소가 우리 좌표에서 878m 떨어져 있어 파이프라인 16단계(카카오 보충)의
 * 200m 게이트를 통과하지 못했다. 그래서 {@code kakao_phone_seed}·{@code kakao_place_seed} 양쪽 모두
 * BRW-051이 NO_MATCH다.
 * <p>
 * ★878m가 벌어진 이유는 우리 DB 주소의 "장섭읍"이 장성읍 오타로 보이기 때문이다. 주소와 좌표는 봉인 정책상
 * 고치지 않는다 — raw 원문 보존({@link UnreachableHomepagePolicy}가 homepage_url을 다루는 방식과 같은 사상).
 * 878m 차이는 수용한다.
 * <p>
 * ★DB(brewery.phone·kakao_place_url)는 건드리지 않는다. 응답 조립 시점에만 얹는다.
 * <p>
 * ★이 목록은 낡는다. 파이프라인 재수집으로 실제 값이 들어오면 null 가드 때문에 이 정책은 자동으로 비활성화된다 —
 * 기존 값을 덮어쓰는 경로가 아예 없다.
 * <p>
 * ★{@link UnreachableHomepagePolicy}와 짝이다. BRW-051은 그쪽에서 homepageUrl이 null로 내려가므로,
 * 이 정책이 없으면 연락 버튼 3개가 전부 비활성이 된다.
 */
public final class ContactSupplementPolicy {

    /** 보충 대상. ★실측이 번복 가능한 값이라 상수로 분리한다. 범용 구조로 넓히지 않는다 — 대상은 한 곳뿐이다. */
    private static final String BREWERY_ID = "BRW-051";
    private static final String PHONE = "061-393-4141";
    private static final String KAKAO_PLACE_URL = "http://place.map.kakao.com/17505055";

    private ContactSupplementPolicy() {
    }

    /** 보충 대상이고 전화가 없을 때만 실측값을 얹는다. 기존 값은 그대로 돌려준다. */
    public static String phone(String breweryId, String currentPhone) {
        return currentPhone == null && BREWERY_ID.equals(breweryId) ? PHONE : currentPhone;
    }

    /**
     * 전화 출처. ★우리가 전화를 채운 경우에만 KAKAO를 붙인다 — {@code currentPhone}을 받는 이유가 이것이다.
     * 전화가 이미 있으면(TOUR 백필 등) 그 출처를 건드리지 않는다.
     */
    public static PhoneSource phoneSource(String breweryId, String currentPhone, PhoneSource currentSource) {
        return currentPhone == null && BREWERY_ID.equals(breweryId) ? PhoneSource.KAKAO : currentSource;
    }

    /** 보충 대상이고 place URL이 없을 때만 실측값을 얹는다. 기존 값은 그대로 돌려준다. */
    public static String kakaoPlaceUrl(String breweryId, String currentUrl) {
        return currentUrl == null && BREWERY_ID.equals(breweryId) ? KAKAO_PLACE_URL : currentUrl;
    }
}
