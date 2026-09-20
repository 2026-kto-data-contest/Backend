package com.jeontongjuro.backend.brewery;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 양조장 정적 사진(classpath:/static/recommended-courses/{breweryId}.png)의 절대 URL 생성기.
 * 관광공사 대표 이미지가 없을 때의 fallback 값을 만든다 — 우선순위 판단은 하지 않는다(호출자 몫).
 * <p>
 * ★같은 breweryId에 대해 상세 API({@code /api/v1/breweries/{id}}의 mainImage.url)와
 * 지도 상세({@code /api/v1/map/places/{id}}의 imageUrl)가 <b>같은 문자열</b>이어야 한다는 요건 때문에
 * 두 곳이 이 클래스 하나를 공유한다. 여기를 고치면 두 API가 함께 움직인다 — 한쪽만 바뀌지 않는다.
 * <p>
 * ★<b>HTTP 요청 컨텍스트가 필요하다.</b> {@link ServletUriComponentsBuilder#fromCurrentContextPath()}가
 * {@code RequestContextHolder}에 묶인 요청에서 현재 origin을 읽기 때문이다.
 * 컨트롤러를 경유한 호출에서만 쓸 수 있고, 배치·스케줄러·요청 밖 스레드에서 부르면
 * {@code IllegalStateException}이 난다. 테스트에서 쓰려면 MockMvc를 타거나
 * {@code RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()))}로
 * 컨텍스트를 직접 묶어야 한다.
 * <p>
 * ★절대 URL로 내리는 이유: 프론트 배포지는 알 수 없는 상대경로를 index.html로 rewrite할 수 있다.
 * "상황에 따라 상대/절대" 같은 방어는 넣지 않는다 — 그러면 두 API의 값이 갈려 위 동일성 요건이 깨진다.
 * <p>
 * ★{@code RecommendedCourseListService}에 같은 경로를 만드는 코드가 따로 있다. 그쪽은 <b>상대경로</b>를
 * 반환하며 <b>의도적으로 통합하지 않았다</b> — 공용으로 바꾸면 추천 코스 응답의 이미지 URL이
 * 상대에서 절대로 바뀐다(응답 변경). 중복으로 보이더라도 합치지 마라.
 */
public final class BreweryStaticImageUrls {

    private BreweryStaticImageUrls() {
    }

    /**
     * 이 양조장의 정적 사진 절대 URL. 파일이 없으면 {@code null}.
     * <p>
     * ★리소스 존재 확인을 유지하는 이유: 파일 없는 양조장에 죽은 URL을 내리지 않기 위해서다.
     * 현재 노출 대상 안에는 해당 양조장이 없지만, 데이터가 늘면 다시 생긴다.
     */
    public static String url(String breweryId) {
        if (breweryId == null || breweryId.isBlank()) {
            return null;
        }
        String assetPath = "/recommended-courses/" + breweryId + ".png";
        if (BreweryStaticImageUrls.class.getResource("/static" + assetPath) == null) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(assetPath)
                .toUriString();
    }
}
