package com.jeontongjuro.backend.brewery;

import java.util.Set;

/**
 * 홈페이지가 열리지 않는 양조장 정책. 상세 응답에서 homepageUrl을 내리지 않기 위한 고정 목록이다.
 * <p>
 * ★2026-09-19에 DNS 조회·HTTP 접속으로 직접 확인한 결과를 박아둔 목록이다. 런타임 판정이 아니다.
 * 확인 시점의 실패 유형:
 * <ul>
 *   <li>DNS 실패 3곳 — BRW-021(배혜정도가 {@code www.baedoga.co.kr}),
 *       BRW-051(청산녹수 {@code www.bluegreenkorea.co.kr}),
 *       BRW-053(태인합동주조장 {@code www.태인양조장.com})</li>
 *   <li>타임아웃 1곳 — BRW-001(갈기산 {@code www.four-m.net})</li>
 *   <li>연결 거부 1곳 — BRW-054(풍정사계 {@code www.hwayang.co})</li>
 * </ul>
 * <p>
 * ★요청마다 DNS·HTTP로 확인하지 않는 이유: 상세 응답이 이미 1.92초라 외부 왕복을 더할 수 없다.
 * 그래서 확인 결과를 상수로 고정한다 — 도메인이 되살아나면 이 목록은 낡는다. 되살아난 곳은 손으로 지워야 한다.
 * <p>
 * ★DB 원문(brewery.homepage_url)은 고치지 않는다 — raw 원문 보존 원칙. 응답에서만 내린다.
 * <p>
 * ★BRW-051은 여기서 homepageUrl이 null로 내려가지만 연락 버튼 3개가 전부 비활성이 되지는 않는다 —
 * phone·kakao_place_url이 DB에 없는 대신 {@link ContactSupplementPolicy}가 응답에서 보충한다.
 * 홈페이지는 도메인 자체가 죽어 있어 보충 대상이 아니다.
 */
public final class UnreachableHomepagePolicy {

    /** 홈페이지 미노출 대상. ★기획·실측이 번복 가능한 값이라 상수로 분리한다. */
    private static final Set<String> UNREACHABLE_BREWERY_IDS =
            Set.of("BRW-001", "BRW-021", "BRW-051", "BRW-053", "BRW-054");

    private UnreachableHomepagePolicy() {
    }

    /**
     * 이 양조장의 홈페이지를 응답에 내려도 되는지. 목록에 있으면 false.
     * ★null 가드는 {@link BreweryVisibilityPolicy}와 달리 판정을 뒤집지 않는다 — {@code Set.of}가
     * {@code contains(null)}에 NPE를 던지기 때문이지, null을 미노출로 볼 이유가 있어서가 아니다.
     */
    public static boolean isReachable(String breweryId) {
        return breweryId == null || !UNREACHABLE_BREWERY_IDS.contains(breweryId);
    }
}
