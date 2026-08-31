package com.jeontongjuro.backend.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.geo.GeoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KakaoPlaceSearchClientImplTest {

    private final KakaoPlaceSearchClientImpl client = new KakaoPlaceSearchClientImpl(
            new GeoProperties("test-key", "https://dapi.kakao.com"), RestClient.builder(), new ObjectMapper());

    @Test
    void exactNameWinsAndCategoryLeafAndHttpsUrlAreReturned() {
        String body = """
                {"documents":[
                  {"id":"1","place_name":"메이비 제주점","category_name":"음식점 > 카페","place_url":"http://place.map.kakao.com/1"},
                  {"id":"2","place_name":"메이비","category_name":"음식점 > 카페 > 테마카페","place_url":"http://place.map.kakao.com/2"}
                ]}
                """;

        KakaoPlaceMatch match = client.bestMatch(body, "메이비").orElseThrow();

        assertThat(match.placeId()).isEqualTo("2");
        assertThat(match.categoryName()).isEqualTo("테마카페");
        assertThat(match.placeUrl()).isEqualTo("https://place.map.kakao.com/2");
    }

    @Test
    void missingDocumentsReturnsEmpty() {
        assertThat(client.bestMatch("{\"documents\":[]}", "없는 장소")).isEmpty();
    }
}
