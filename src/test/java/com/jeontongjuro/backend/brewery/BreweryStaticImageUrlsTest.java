package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 양조장 정적 사진 URL 생성 규칙 검증. 상세 API와 지도 상세가 이 클래스 하나를 공유하므로,
 * 여기가 깨지면 두 API가 함께 깨진다.
 * <p>
 * ★{@link RequestContextHolder}를 직접 묶는다 — {@code ServletUriComponentsBuilder.fromCurrentContextPath()}가
 * 현재 요청에서 origin을 읽기 때문이다(프로덕션은 컨트롤러 경유라 항상 있다).
 * 누수 방지로 {@code @AfterEach}에서 반드시 해제한다.
 */
class BreweryStaticImageUrlsTest {

    /** 정적 사진이 실제로 존재하는 양조장. 파일이 지워지면 이 테스트가 먼저 깨진다(의도). */
    private static final String BREWERY_WITH_PNG = "BRW-001";
    /** 정적 사진이 없는 양조장 ID. */
    private static final String BREWERY_WITHOUT_PNG = "BRW-999";

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("정적 사진이 있으면 현재 origin을 포함한 절대 URL을 만든다")
    void absoluteUrlWhenPngExists() {
        assertThat(BreweryStaticImageUrls.url(BREWERY_WITH_PNG))
                .isEqualTo("http://localhost/recommended-courses/" + BREWERY_WITH_PNG + ".png");
    }

    @Test
    @DisplayName("정적 사진이 없으면 null — 죽은 URL을 내리지 않는다")
    void nullWhenPngMissing() {
        assertThat(BreweryStaticImageUrls.url(BREWERY_WITHOUT_PNG)).isNull();
    }

    @Test
    @DisplayName("breweryId가 null·공백이면 null(리소스 조회도 하지 않는다)")
    void nullWhenBreweryIdBlank() {
        assertThat(BreweryStaticImageUrls.url(null)).isNull();
        assertThat(BreweryStaticImageUrls.url("")).isNull();
        assertThat(BreweryStaticImageUrls.url("   ")).isNull();
    }

    @Test
    @DisplayName("요청 컨텍스트가 없으면 IllegalStateException — 배치·스케줄러에서 부를 수 없다")
    void requiresRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> BreweryStaticImageUrls.url(BREWERY_WITH_PNG))
                .isInstanceOf(IllegalStateException.class);
    }
}
