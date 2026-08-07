package com.jeontongjuro.backend.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * tour_match_seed.json 로더(전제 10 확정 20건). AddressFixSeedLoadService 패턴 — {@code JsonNode} 직접
 * 접근, 주입 {@link ObjectMapper} 사용. 파싱 대상은 {@code brewery_id}·{@code content_id}뿐이며
 * {@code source_title}·{@code _meta}는 추적용이라 읽지 않는다.
 * <p>
 * ★MANUAL wins: 이 시드가 매칭의 진실이다. 자동 파생(캐시 distance≤200)은 교차검증 리포트 전용이며 여기서
 * 시드를 덮어쓰지 않는다.
 */
@Service
public class TourMatchSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(TourMatchSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/tour_match_seed.json";

    private final ObjectMapper objectMapper;
    private final List<SeedEntry> seeds = new ArrayList<>();

    public TourMatchSeedLoadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 확정 시드 1건 (brewery_id → content_id). */
    public record SeedEntry(String breweryId, String contentId) {
    }

    @PostConstruct
    void load() {
        JsonNode arr = readSeeds();
        for (JsonNode s : arr) {
            String breweryId = s.get("brewery_id").asText();
            String contentId = s.get("content_id").asText();
            seeds.add(new SeedEntry(breweryId, contentId));
        }
        log.info("[tour] tour_match_seed 로드 {}건", seeds.size());
    }

    /** 확정 시드 목록(입력 순서 보존). */
    public List<SeedEntry> entries() {
        return List.copyOf(seeds);
    }

    private JsonNode readSeeds() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("tour_match_seed 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode seedsNode = objectMapper.readTree(in).get("seeds");
            if (seedsNode == null || !seedsNode.isArray()) {
                throw new IllegalStateException("tour_match_seed 형식 오류: seeds 배열 없음 — " + SEED_CLASSPATH);
            }
            return seedsNode;
        } catch (IOException ex) {
            throw new IllegalStateException("tour_match_seed 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
