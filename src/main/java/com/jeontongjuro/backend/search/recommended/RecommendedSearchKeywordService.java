package com.jeontongjuro.backend.search.recommended;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검색 화면에 운영이 지정한 고정 추천 검색어를 배포 리소스 순서대로 제공한다. */
@Service
@Transactional(readOnly = true)
public class RecommendedSearchKeywordService {

    private static final String RESOURCE = "/recommended_search_keywords.json";

    private static final int MAX_KEYWORD_COUNT = 10;
    private final List<String> candidateKeywords;

    public RecommendedSearchKeywordService(ObjectMapper objectMapper) {
        this.candidateKeywords = load(objectMapper);
    }

    public List<RecommendedSearchKeywordResponse> list() {
        return candidateKeywords.stream()
                .limit(MAX_KEYWORD_COUNT)
                .map(RecommendedSearchKeywordResponse::new)
                .toList();
    }

    private static List<String> load(ObjectMapper objectMapper) {
        try (InputStream in = RecommendedSearchKeywordService.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("추천 검색어 리소스 없음: " + RESOURCE);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("추천 검색어 리소스 형식 오류: entries 배열 없음 — " + RESOURCE);
            }
            if (entries.size() > MAX_KEYWORD_COUNT) {
                throw new IllegalStateException("추천 검색어는 최대 " + MAX_KEYWORD_COUNT + "개까지 설정할 수 있습니다 — " + RESOURCE);
            }

            Set<String> seen = new HashSet<>();
            List<String> loaded = new java.util.ArrayList<>();
            for (JsonNode entry : entries) {
                JsonNode keyword = entry.get("keyword");
                if (keyword == null || !keyword.isTextual() || keyword.asText().isBlank()) {
                    throw new IllegalStateException("추천 검색어 항목 형식 오류 — " + RESOURCE);
                }
                if (!seen.add(keyword.asText())) {
                    throw new IllegalStateException("추천 검색어 중복 항목 — " + keyword.asText());
                }
                loaded.add(keyword.asText());
            }
            return List.copyOf(loaded);
        } catch (IOException ex) {
            throw new IllegalStateException("추천 검색어 리소스 읽기 실패: " + RESOURCE, ex);
        }
    }
}
