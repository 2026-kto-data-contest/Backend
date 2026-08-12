package com.jeontongjuro.backend.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * experience_match_seed.json 로더(양조장명 → brewery_id 매칭 30건). TourMatchSeedLoadService 패턴 —
 * {@code JsonNode} 직접 접근, 주입 {@link ObjectMapper} 사용. 파싱 대상은 {@code experienceBreweryName}·
 * {@code breweryId}뿐이며 {@code matchMethod}·{@code evidence}·{@code _meta}는 추적용이라 읽지 않는다.
 * <p>
 * ★시드가 매칭의 진실이다: 체험 상세값은 API에서 읽고, 어느 양조장인지는 이 시드만으로 정한다. API 응답에
 * 시드에 없는 양조장명이 오면 {@link ExperienceRollupService}가 fail-fast한다(원본 갱신 신호 — 사람이 봐야 함).
 */
@Service
public class ExperienceMatchSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceMatchSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/experience_match_seed.json";

    private final ObjectMapper objectMapper;
    /** 양조장명 → brewery_id. 입력 순서 보존(LinkedHashMap) — 디버깅 가독성. */
    private final Map<String, String> breweryIdByName = new LinkedHashMap<>();

    public ExperienceMatchSeedLoadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        JsonNode matches = readMatches();
        for (JsonNode m : matches) {
            String name = m.get("experienceBreweryName").asText();
            String breweryId = m.get("breweryId").asText();
            String prev = breweryIdByName.put(name, breweryId);
            if (prev != null && !prev.equals(breweryId)) {
                // 같은 양조장명이 두 brewery_id로 매핑되면 시드 자체가 모순 — 조용히 덮지 않고 중단.
                throw new IllegalStateException(
                        "체험 시드 모순: 양조장명 '" + name + "'이 " + prev + "·" + breweryId + " 두 곳에 매핑");
            }
        }
        log.info("[experience] experience_match_seed 로드 {}건", breweryIdByName.size());
    }

    /** 양조장명 매칭 조회 — 없으면 null(호출자가 fail-fast 판단). */
    public String breweryIdOf(String experienceBreweryName) {
        return breweryIdByName.get(experienceBreweryName);
    }

    /** 시드 매칭 건수(로그·리포트용). */
    public int size() {
        return breweryIdByName.size();
    }

    private JsonNode readMatches() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("experience_match_seed 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode node = objectMapper.readTree(in).get("matches");
            if (node == null || !node.isArray()) {
                throw new IllegalStateException(
                        "experience_match_seed 형식 오류: matches 배열 없음 — " + SEED_CLASSPATH);
            }
            return node;
        } catch (IOException ex) {
            throw new IllegalStateException("experience_match_seed 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
