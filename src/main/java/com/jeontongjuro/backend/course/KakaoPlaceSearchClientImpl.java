package com.jeontongjuro.backend.course;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.geo.GeoProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 카카오 키워드 장소 검색으로 실제 장소 ID·상세 링크·세부 분류를 보강한다. */
@Component
public class KakaoPlaceSearchClientImpl implements KakaoPlaceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceSearchClientImpl.class);
    private static final String SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int SEARCH_RADIUS_METERS = 2_000;

    private final GeoProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Optional<KakaoPlaceMatch>> cache = new ConcurrentHashMap<>();

    public KakaoPlaceSearchClientImpl(GeoProperties properties, RestClient.Builder builder,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.clone().requestFactory(requestFactory).baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<KakaoPlaceMatch> findPlace(String name, BigDecimal latitude, BigDecimal longitude) {
        if (name == null || name.isBlank() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Optional.empty();
        }
        String cacheKey = normalize(name) + ":" + latitude + ":" + longitude;
        return cache.computeIfAbsent(cacheKey, ignored -> requestPlace(name, latitude, longitude));
    }

    private Optional<KakaoPlaceMatch> requestPlace(String name, BigDecimal latitude, BigDecimal longitude) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder.path(SEARCH_PATH).queryParam("query", name).queryParam("size", 5);
                        if (latitude != null && longitude != null) {
                            uri.queryParam("x", longitude.toPlainString())
                                    .queryParam("y", latitude.toPlainString())
                                    .queryParam("radius", SEARCH_RADIUS_METERS)
                                    .queryParam("sort", "distance");
                        }
                        return uri.build();
                    })
                    .header("Authorization", "KakaoAK " + properties.apiKey())
                    .retrieve().body(String.class);
            return bestMatch(body, name);
        } catch (RestClientException | IllegalStateException e) {
            // 외부 API 장애가 코스 조회 전체 장애로 번지지 않도록 TourAPI 기반 값으로 폴백한다.
            log.warn("카카오 장소 검색 실패, 기본 링크로 폴백 name='{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    Optional<KakaoPlaceMatch> bestMatch(String body, String requestedName) {
        if (body == null || body.isBlank()) return Optional.empty();
        try {
            JsonNode documents = objectMapper.readTree(body).path("documents");
            if (!documents.isArray() || documents.isEmpty()) return Optional.empty();
            String normalizedRequest = normalize(requestedName);
            JsonNode selected = documents.get(0);
            for (JsonNode document : documents) {
                if (normalize(document.path("place_name").asText()).equals(normalizedRequest)) {
                    selected = document;
                    break;
                }
            }
            String id = text(selected, "id");
            String url = text(selected, "place_url");
            if (url != null && url.startsWith("http://")) url = "https://" + url.substring(7);
            String category = leafCategory(text(selected, "category_name"));
            if (url == null && id != null) url = "https://place.map.kakao.com/" + id;
            if (id == null && url == null && category == null) return Optional.empty();
            return Optional.of(new KakaoPlaceMatch(id, url, category));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 장소 검색 JSON 파싱 실패", e);
        }
    }

    private String leafCategory(String categoryPath) {
        if (categoryPath == null) return null;
        String[] parts = categoryPath.split("\\s*>\\s*");
        return parts.length == 0 ? null : parts[parts.length - 1].strip();
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
    }
}
