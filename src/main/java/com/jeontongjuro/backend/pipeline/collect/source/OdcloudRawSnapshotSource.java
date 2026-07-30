package com.jeontongjuro.backend.pipeline.collect.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jeontongjuro.backend.pipeline.collect.CollectProperties;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 라이브 odcloud 수집 구현. Base URL {api-base}, page/perPage 페이지네이션.
 *
 * 순서 보장(RawSnapshot 계약 이행):
 * page=1부터 오름차순으로 "순차" 호출하고, 각 응답의 data 배열을 응답에 실린 순서 그대로 이어붙인다.
 * 따라서 병합 리스트의 인덱스 == (page-1)*perPage + 페이지 내 위치 == 원본 totalCount 순서의 전역 위치.
 * 병렬 호출·도착 순서 병합을 하지 않으므로 수집 순서가 원본 순서를 훼손할 수 없다.
 * 수집 도중 totalCount가 변하면(원천 데이터 변동) 순서 보장이 깨지므로 즉시 실패시킨다.
 *
 * serviceKey는 환경변수 AT_SERVICE_KEY 에서 설정으로 주입된 값만 사용하며 로그·예외 메시지에 싣지 않는다.
 */
@Component
public class OdcloudRawSnapshotSource implements RawSnapshotSource {

    private final CollectProperties properties;
    private final RestClient restClient;

    public OdcloudRawSnapshotSource(CollectProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.apiBase()).build();
    }

    @Override
    public RawSnapshot fetch(RawDataset dataset) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new IllegalStateException("serviceKey 미설정 — 환경변수 AT_SERVICE_KEY 를 설정한 뒤 실행하라.");
        }
        CollectProperties.Dataset spec = properties.of(dataset);
        List<JsonNode> rows = new ArrayList<>();
        long totalCount = -1;
        int page = 1;
        while (true) {
            JsonNode body = fetchPage(spec, page);
            long pageTotal = body.path("totalCount").asLong(-1);
            if (pageTotal < 0) {
                throw new IllegalStateException(dataset + " page " + page + " 응답에 totalCount 없음");
            }
            if (totalCount < 0) {
                totalCount = pageTotal;
            } else if (totalCount != pageTotal) {
                // 수집 도중 원천 변동 → 전역 인덱스의 원본 순서 보장이 불가하므로 중단
                throw new IllegalStateException(dataset + " 수집 중 totalCount 변동(" + totalCount + " → " + pageTotal + ")");
            }
            JsonNode data = body.path("data");
            if (!data.isArray()) {
                throw new IllegalStateException(dataset + " page " + page + " 응답에 data 배열 없음");
            }
            data.forEach(rows::add); // 응답 순서 그대로 append
            if (rows.size() >= totalCount || data.isEmpty()) {
                break;
            }
            page++;
        }
        if (rows.size() != totalCount) {
            throw new IllegalStateException(dataset + " 수집 행수 " + rows.size() + " != totalCount " + totalCount);
        }
        return new RawSnapshot(dataset, Instant.now(), totalCount, List.copyOf(rows));
    }

    private JsonNode fetchPage(CollectProperties.Dataset spec, int page) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(spec.path())
                        .queryParam("page", page)
                        .queryParam("perPage", spec.perPage())
                        .queryParam("returnType", "JSON")
                        .queryParam("serviceKey", properties.serviceKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }
}
