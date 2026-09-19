package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 연락처 보충 정책 단위 검증(DB 없음). 2026-09-19 카카오 로컬 실측으로 고정한 BRW-051 한 곳을 봉인한다.
 * 응답 경로에서의 실제 적용은 {@code BreweryDetailApiTest}·{@code MapPlaceDetailServiceTest}가 별도로 잡는다.
 */
class ContactSupplementPolicyTest {

    @Test
    @DisplayName("BRW-051 + 값 없음 → 실측 전화·place URL을 보충하고 출처는 KAKAO")
    void supplementsTargetWhenAbsent() {
        assertThat(ContactSupplementPolicy.phone("BRW-051", null)).isEqualTo("061-393-4141");
        assertThat(ContactSupplementPolicy.phoneSource("BRW-051", null, null)).isEqualTo(PhoneSource.KAKAO);
        assertThat(ContactSupplementPolicy.kakaoPlaceUrl("BRW-051", null))
                .isEqualTo("http://place.map.kakao.com/17505055");
    }

    @Test
    @DisplayName("BRW-051이라도 값이 있으면 덮어쓰지 않는다 — 재수집이 들어오면 정책이 스스로 비켜난다")
    void neverOverwritesExistingValues() {
        assertThat(ContactSupplementPolicy.phone("BRW-051", "061-000-0000")).isEqualTo("061-000-0000");
        assertThat(ContactSupplementPolicy.phoneSource("BRW-051", "061-000-0000", PhoneSource.TOUR))
                .isEqualTo(PhoneSource.TOUR);
        assertThat(ContactSupplementPolicy.kakaoPlaceUrl("BRW-051", "http://place.map.kakao.com/1"))
                .isEqualTo("http://place.map.kakao.com/1");
    }

    @Test
    @DisplayName("전화가 이미 있으면 출처는 건드리지 않는다(phoneSource가 currentPhone을 받는 이유)")
    void phoneSourceFollowsWhoFilledThePhone() {
        // 전화는 있는데 출처만 비어 있는 상태 — 우리가 채운 게 아니므로 KAKAO를 붙이지 않는다.
        assertThat(ContactSupplementPolicy.phoneSource("BRW-051", "061-000-0000", null)).isNull();
    }

    @Test
    @DisplayName("대상 밖 양조장 → 어떤 필드도 보충하지 않는다")
    void othersAreUntouched() {
        for (String id : new String[] {"BRW-001", "BRW-003", "BRW-027", "BRW-036", "BRW-040", "BRW-050",
                                       "BRW-052", "BRW-053"}) {
            assertThat(ContactSupplementPolicy.phone(id, null)).as("%s 전화는 비워 둔다", id).isNull();
            assertThat(ContactSupplementPolicy.phoneSource(id, null, null))
                    .as("%s 전화 출처는 비워 둔다", id).isNull();
            assertThat(ContactSupplementPolicy.kakaoPlaceUrl(id, null))
                    .as("%s place URL은 비워 둔다", id).isNull();
        }
    }

    @Test
    @DisplayName("null·미등록 ID → 보충 대상이 아님")
    void nullAndUnknownAreUntouched() {
        assertThat(ContactSupplementPolicy.phone(null, null)).isNull();
        assertThat(ContactSupplementPolicy.phoneSource(null, null, null)).isNull();
        assertThat(ContactSupplementPolicy.kakaoPlaceUrl(null, null)).isNull();
        assertThat(ContactSupplementPolicy.phone("BRW-999", null)).isNull();
        assertThat(ContactSupplementPolicy.kakaoPlaceUrl("BRW-999", null)).isNull();
    }
}
