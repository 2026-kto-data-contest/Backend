package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 홈페이지 미노출 목록 단위 검증(DB 없음). 2026-09-19 실측으로 고정한 5곳을 봉인한다.
 * 응답 경로(상세 API)에서의 실제 게이팅은 {@code BreweryDetailApiTest}가 별도로 잡는다.
 */
class UnreachableHomepagePolicyTest {

    @Test
    @DisplayName("DNS 실패 3곳(BRW-021·051·053) → isReachable=false")
    void dnsFailuresAreUnreachable() {
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-021")).isFalse();
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-051")).isFalse();
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-053")).isFalse();
    }

    @Test
    @DisplayName("타임아웃 1곳(BRW-001) → isReachable=false")
    void timeoutIsUnreachable() {
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-001")).isFalse();
    }

    @Test
    @DisplayName("연결 거부 1곳(BRW-054) → isReachable=false")
    void connectionRefusedIsUnreachable() {
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-054")).isFalse();
    }

    @Test
    @DisplayName("목록 밖 양조장 → isReachable=true (한글 도메인·봇 차단은 살아 있는 사이트라 막지 않는다)")
    void othersAreReachable() {
        // BRW-002·049: 한글 도메인 — punycode 변환 시 200. BRW-004·014·026: 봇 차단(999·406·429)이나 사이트는 생존.
        for (String id : new String[] {"BRW-002", "BRW-003", "BRW-004", "BRW-006", "BRW-014",
                                       "BRW-018", "BRW-026", "BRW-049", "BRW-059"}) {
            assertThat(UnreachableHomepagePolicy.isReachable(id))
                    .as("목록 밖 양조장 %s은 홈페이지를 그대로 내린다", id).isTrue();
        }
    }

    @Test
    @DisplayName("null·미등록 ID → isReachable=true (게이팅 대상이 아님)")
    void nullAndUnknownAreReachable() {
        assertThat(UnreachableHomepagePolicy.isReachable(null)).isTrue();
        assertThat(UnreachableHomepagePolicy.isReachable("BRW-999")).isTrue();
    }
}
