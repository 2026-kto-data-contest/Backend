package com.jeontongjuro.backend.tour;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TourAPI(KorService2) 운영 클라이언트. locationBasedList2(반경 목록)·detailCommon2(단건 상세).
 * <p>
 * Jackson 2/3 우회(collect·geo와 동일): Framework 7 RestClient 기본 컨버터는 Jackson 3라 Jackson 2 노드로
 * 직접 역직렬화하면 죽는다 — 원문을 {@code String}으로 받아 주입된 Jackson 2 {@link ObjectMapper}로 재파싱.
 * <p>
 * serviceKey는 디코드 원문을 {@code queryParam}으로 넘겨 UriBuilder가 인코딩한다(collect·스카우트 curl
 * {@code --data-urlencode}와 동일). blank면 첫 호출에서 명확히 중단(생성 시점 아님 — 스텁 테스트가 빈만
 * 구성하고 호출 안 할 수 있게).
 * <p>
 * ★페이징: numOfRows=100이라 대부분 다중 페이지가 상시로 돈다(전제 11 상한 미검증 대비). 매 응답
 * {@code X-RateLimit-Remaining}를 로깅하고 임계 미만이면 예산 소진 전에 fail-fast로 중단한다.
 */
@Component
public class TourApiClientImpl implements TourApiClient {

    private static final Logger log = LoggerFactory.getLogger(TourApiClientImpl.class);
    private static final String LOCATION_PATH = "/locationBasedList2";
    private static final String DETAIL_PATH = "/detailCommon2";
    private static final String RATELIMIT_HEADER = "X-RateLimit-Remaining";
    /** 잔량이 이 값 미만이면 중단(예산 소진 방어 — 사람이 확인 후 다음날 재개). */
    private static final int RATELIMIT_FLOOR = 50;
    /** 폭주 방어 상한(한 양조장 페이지 수). 정상은 강화 5·부산 8 수준 — 초과는 이상신호로 중단. */
    private static final int MAX_PAGES = 200;

    private final TourProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TourApiClientImpl(TourProperties properties, RestClient.Builder restClientBuilder,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TourContentRow> locationBasedList(BigDecimal latitude, BigDecimal longitude, int radiusM) {
        requireServiceKey();
        String mapX = longitude.toPlainString();
        String mapY = latitude.toPlainString();
        List<TourContentRow> all = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE; // 첫 응답에서 확정
        while (pageNo <= MAX_PAGES) {
            throttle();
            final int page = pageNo;
            ResponseEntity<String> resp = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(LOCATION_PATH)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", properties.mobileApp())
                            .queryParam("_type", "json")
                            .queryParam("numOfRows", properties.numOfRows())
                            .queryParam("pageNo", page)
                            .queryParam("mapX", mapX)
                            .queryParam("mapY", mapY)
                            .queryParam("radius", radiusM)
                            .build())
                    .retrieve()
                    .toEntity(String.class);
            guardRateLimit(resp);
            JsonNode body = requireBody(resp, LOCATION_PATH + " page " + page);

            if (page == 1) {
                totalCount = body.path("totalCount").asInt(0);
            }
            List<TourContentRow> rows = extractItems(body);
            all.addAll(rows);
            log.info("[tour] locationBasedList2 mapX={} mapY={} radius={} page={} rows={} 누적={}/{}",
                    mapX, mapY, radiusM, page, rows.size(), all.size(), totalCount);

            if (all.size() >= totalCount || rows.isEmpty()) {
                break;
            }
            pageNo++;
        }
        if (pageNo > MAX_PAGES) {
            throw new IllegalStateException(
                    "locationBasedList2 페이지 상한(" + MAX_PAGES + ") 초과 — 이상 응답 의심 mapX=" + mapX + " mapY=" + mapY);
        }
        return all;
    }

    @Override
    public Optional<TourContentRow> detailCommon(String contentId) {
        requireServiceKey();
        throttle();
        ResponseEntity<String> resp = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(DETAIL_PATH)
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json")
                        .queryParam("contentId", contentId)
                        .build())
                .retrieve()
                .toEntity(String.class);
        guardRateLimit(resp);
        JsonNode body = requireBody(resp, DETAIL_PATH + " contentId=" + contentId);
        List<TourContentRow> rows = extractItems(body);
        if (rows.isEmpty()) {
            log.warn("[tour] detailCommon2 결과 없음 contentId={}", contentId);
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /** response.body 노드 확보 + resultCode 게이트. items 없음(전제 9)은 여기서 판단하지 않는다. */
    private JsonNode requireBody(ResponseEntity<String> resp, String label) {
        String raw = resp.getBody();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("TourAPI 빈 응답 " + label);
        }
        JsonNode root = parse(raw, label);
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText("");
        if (!"0000".equals(resultCode)) {
            // resultMsg는 실어도 되나(키 아님) 원문 전체는 싣지 않는다(serviceKey 에코 차단).
            throw new IllegalStateException("TourAPI 오류 " + label
                    + " resultCode=" + resultCode + " resultMsg=" + header.path("resultMsg").asText(""));
        }
        return root.path("response").path("body");
    }

    /** items가 ""(빈 문자열)·부재면 빈 리스트(전제 9). item이 단건 객체·배열 양쪽 모두 처리. */
    private List<TourContentRow> extractItems(JsonNode body) {
        JsonNode items = body.path("items");
        if (!items.isObject()) {
            return List.of(); // ""(TextNode) 또는 부재 → 결과 없음
        }
        JsonNode item = items.path("item");
        List<TourContentRow> rows = new ArrayList<>();
        if (item.isArray()) {
            for (JsonNode n : item) {
                rows.add(toRow(n));
            }
        } else if (item.isObject()) {
            rows.add(toRow(item));
        }
        return rows;
    }

    private TourContentRow toRow(JsonNode n) {
        return new TourContentRow(
                text(n, "contentid"), text(n, "contenttypeid"), text(n, "title"),
                text(n, "addr1"), text(n, "addr2"), text(n, "zipcode"),
                text(n, "areacode"), text(n, "sigungucode"),
                text(n, "cat1"), text(n, "cat2"), text(n, "cat3"),
                text(n, "lclsSystm1"), text(n, "lclsSystm2"), text(n, "lclsSystm3"),
                text(n, "lDongRegnCd"), text(n, "lDongSignguCd"),
                text(n, "mapx"), text(n, "mapy"), text(n, "mlevel"),
                text(n, "firstimage"), text(n, "firstimage2"), text(n, "cpyrhtDivCd"),
                text(n, "createdtime"), text(n, "modifiedtime"),
                distMeters(n));
    }

    /** 빈 문자열은 null로(결측률 쿼리를 의미있게 — first_image IS NULL == 결측). */
    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText(null);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static Double distMeters(JsonNode n) {
        String v = n.path("dist").asText(null);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode parse(String body, String label) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TourAPI JSON 파싱 실패 " + label, e);
        }
    }

    private void guardRateLimit(ResponseEntity<String> resp) {
        String remaining = resp.getHeaders().getFirst(RATELIMIT_HEADER);
        if (remaining == null || remaining.isBlank()) {
            return; // 헤더 부재 시 판단 보류(로깅만 생략)
        }
        try {
            int left = Integer.parseInt(remaining.trim());
            log.info("[tour] {} 잔량 {}", RATELIMIT_HEADER, left);
            if (left < RATELIMIT_FLOOR) {
                throw new IllegalStateException(
                        "TourAPI 호출 예산 임계 미만(잔량 " + left + " < " + RATELIMIT_FLOOR
                                + ") — 소진 방어 중단. 다음날 재개하라(멱등 재개).");
            }
        } catch (NumberFormatException e) {
            // 숫자 아님 → 판단 보류
        }
    }

    private void requireServiceKey() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new IllegalStateException(
                    "tour.api.service-key 미설정 — 환경변수 AT_SERVICE_KEY 를 주입한 뒤 실행하라(source ~/.at_service_key).");
        }
    }

    private void throttle() {
        try {
            Thread.sleep(properties.throttleMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI 호출 간격 대기 중 인터럽트", e);
        }
    }
}
