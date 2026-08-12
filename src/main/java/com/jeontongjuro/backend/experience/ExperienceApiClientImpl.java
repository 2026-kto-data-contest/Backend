package com.jeontongjuro.backend.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 라이브 odcloud 체험 API 클라이언트(15089109). collect {@code OdcloudRawSnapshotSource}와 동일 페이지네이션·
 * Jackson 2/3 우회를 따른다: page=1부터 순차 호출, 각 응답 data를 순서 그대로 append, 원문을 {@code String}으로
 * 받아 주입된 Jackson 2 {@link ObjectMapper}로 재파싱(Framework 7 RestClient 기본 컨버터는 Jackson 3라 Jackson 2
 * 노드 직접 역직렬화가 죽는다).
 * <p>
 * ★모든 실패(키 미설정·HTTP 오류·빈 응답·파싱 실패·totalCount 변동·행수 불일치)를 {@link ExperienceApiException}
 * 으로 던진다 — 15단계 skip 사유가 되어 파이프라인 나머지가 완주한다. serviceKey는 queryParam으로 넘겨 UriBuilder가
 * 인코딩하며 로그·예외 메시지에 싣지 않는다.
 * <p>
 * known gap: 이 라이브 RestClient 역직렬화 경로는 자동 테스트 밖이다(테스트는 스텁 ExperienceApiClient 경유).
 * 검증은 수동 실기동(bootRun --spring.profiles.active=process)으로 한다.
 */
@Component
public class ExperienceApiClientImpl implements ExperienceApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExperienceApiClientImpl.class);

    private final ExperienceApiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ExperienceApiClientImpl(ExperienceApiProperties properties, RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.apiBase()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ExperienceRow> fetchAll() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new ExperienceApiException(
                    "serviceKey 미설정 — 환경변수 AT_SERVICE_KEY 설정 필요(15단계 skip)");
        }
        List<JsonNode> rows = new ArrayList<>();
        long totalCount = -1;
        int page = 1;
        try {
            while (true) {
                JsonNode body = fetchPage(page);
                long pageTotal = body.path("totalCount").asLong(-1);
                if (pageTotal < 0) {
                    throw new ExperienceApiException("체험 API page " + page + " 응답에 totalCount 없음");
                }
                if (totalCount < 0) {
                    totalCount = pageTotal;
                } else if (totalCount != pageTotal) {
                    throw new ExperienceApiException(
                            "체험 API 수집 중 totalCount 변동(" + totalCount + " → " + pageTotal + ")");
                }
                JsonNode data = body.path("data");
                if (!data.isArray()) {
                    throw new ExperienceApiException("체험 API page " + page + " 응답에 data 배열 없음");
                }
                data.forEach(rows::add);
                if (rows.size() >= totalCount || data.isEmpty()) {
                    break;
                }
                page++;
            }
        } catch (ExperienceApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // HTTP 오류·타임아웃 등 RestClient 예외를 skip 신호로 승격(serviceKey 에코 차단 위해 원문 미포함).
            throw new ExperienceApiException("체험 API 호출 실패(15단계 skip)", ex);
        }
        if (rows.size() != totalCount) {
            throw new ExperienceApiException(
                    "체험 API 수집 행수 " + rows.size() + " != totalCount " + totalCount);
        }
        List<ExperienceRow> parsed = new ArrayList<>(rows.size());
        for (JsonNode node : rows) {
            parsed.add(ExperienceRow.fromJson(node));
        }
        log.info("[experience] odcloud 체험 API 수집 {}행(page {}회 호출)", parsed.size(), page);
        return parsed;
    }

    private JsonNode fetchPage(int page) {
        // body(JsonNode.class) 직접 수신 금지 — String으로 받아 Jackson 2 ObjectMapper로 재파싱(위 클래스 주석).
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.path())
                        .queryParam("page", page)
                        .queryParam("perPage", properties.perPage())
                        .queryParam("returnType", "JSON")
                        .queryParam("serviceKey", properties.serviceKey())
                        .build())
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            throw new ExperienceApiException("체험 API page " + page + " 빈 응답");
        }
        try {
            return objectMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 파싱 실패 원문/서비스키를 메시지에 싣지 않는다(응답 serviceKey 에코 가능성 차단).
            throw new ExperienceApiException("체험 API page " + page + " JSON 파싱 실패", e);
        }
    }
}
