package com.jeontongjuro.backend.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 주소 검색({@code /v2/local/search/address.json}) 운영 클라이언트.
 * <p>
 * Jackson 2/3 우회(collect의 {@code OdcloudRawSnapshotSource}와 동일 패턴): Framework 7 RestClient 기본
 * JSON 컨버터는 Jackson 3라 Jackson 2 {@code JsonNode}로 직접 역직렬화하면 죽는다. 그래서 원문을
 * {@code String}으로 받고 주입된 Jackson 2 {@link ObjectMapper}로 재파싱한다.
 * <p>
 * 호출 간 200ms 간격을 둔다(59건 ≈ 12초. 쿼터는 10만/일이나 초당 제한 방어). apiKey 미주입 시
 * 첫 호출에서 명확한 메시지로 중단한다(생성 시점 아님 — 스텁 테스트가 이 빈을 구성만 하고 호출하지 않도록).
 */
@Component
public class KakaoGeocodingClientImpl implements KakaoGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodingClientImpl.class);
    private static final String SEARCH_PATH = "/v2/local/search/address.json";
    private static final long THROTTLE_MILLIS = 200L;

    private final GeoProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoGeocodingClientImpl(GeoProperties properties, RestClient.Builder restClientBuilder,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        // ★골든 경로와 동일한 파이프라인 공용 Jackson 2 매퍼(global.JacksonConfig)로 응답을 파싱한다.
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<KakaoAddressMatch> geocode(String query) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "geo.kakao.api-key 미설정 — 환경변수 KAKAO_REST_API_KEY 를 주입한 뒤 실행하라(source ~/.kakao_key).");
        }
        throttle();

        // 원문을 String으로 받아(StringHttpMessageConverter, Jackson 무관) Jackson 2로 재파싱.
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParam("query", query).build())
                .header("Authorization", "KakaoAK " + properties.apiKey())
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("카카오 주소검색 빈 응답 query=" + query);
        }
        JsonNode root = parse(body, query);

        // ★documents[0] 무검증 채택 금지 — meta로 게이트.
        JsonNode meta = root.path("meta");
        int totalCount = meta.path("total_count").asInt(0);
        int pageableCount = meta.path("pageable_count").asInt(0);
        if (totalCount == 0) {
            log.info("[geo] 무결과(total=0) query='{}' → 폴백", query);
            return Optional.empty();
        }
        JsonNode documents = root.path("documents");
        if (!documents.isArray() || documents.isEmpty()) {
            log.info("[geo] documents 비어있음(total={}) query='{}' → 폴백", totalCount, query);
            return Optional.empty();
        }
        if (totalCount >= 2) {
            // 실측상 0건이지만 게이트는 있어야 한다 — 후보 전체를 남기고 [0] 채택.
            log.warn("[geo] 후보 {}건(total={} pageable={}) query='{}' → documents[0] 채택. 후보 전체: {}",
                    documents.size(), totalCount, pageableCount, query, documents);
        }
        JsonNode d0 = documents.get(0);
        String addressType = d0.path("address_type").asText("");
        // x=경도(lng)/y=위도(lat). 여기서 축을 확정한다.
        BigDecimal lng = new BigDecimal(d0.path("x").asText());
        BigDecimal lat = new BigDecimal(d0.path("y").asText());
        log.info("[geo] 매치 query='{}' total={} pageable={} address_type={} x={} y={}",
                query, totalCount, pageableCount, addressType, lng, lat);
        return Optional.of(new KakaoAddressMatch(lat, lng, addressType));
    }

    private JsonNode parse(String body, String query) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 주소검색 JSON 파싱 실패 query=" + query, e);
        }
    }

    private void throttle() {
        try {
            Thread.sleep(THROTTLE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("지오코딩 호출 간격 대기 중 인터럽트", e);
        }
    }
}
