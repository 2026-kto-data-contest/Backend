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

/** 검색 화면 추천 검색어를 배포 리소스에서 한 번 읽어 제공한다. */
@Service
@Transactional(readOnly = true)
public class RecommendedSearchKeywordService {

    private static final String RESOURCE = "/recommended_search_keywords.json";

    private final List<RecommendedSearchKeywordResponse> keywords;

    public RecommendedSearchKeywordService(ObjectMapper objectMapper) {
        this.keywords = load(objectMapper);
    }

    public List<RecommendedSearchKeywordResponse> list() {
        return keywords;
    }

    private static List<RecommendedSearchKeywordResponse> load(ObjectMapper objectMapper) {
        try (InputStream in = RecommendedSearchKeywordService.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("추천 검색어 리소스 없음: " + RESOURCE);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("추천 검색어 리소스 형식 오류: entries 배열 없음 — " + RESOURCE);
            }

            Set<String> seen = new HashSet<>();
            List<RecommendedSearchKeywordResponse> loaded = new java.util.ArrayList<>();
            for (JsonNode entry : entries) {
                JsonNode keyword = entry.get("keyword");
                JsonNode resultCount = entry.get("resultCount");
                if (keyword == null || !keyword.isTextual() || keyword.asText().isBlank()
                        || resultCount == null || !resultCount.canConvertToInt() || resultCount.asInt() < 5) {
                    throw new IllegalStateException("추천 검색어 항목 형식 오류 — " + RESOURCE);
                }
                if (!seen.add(keyword.asText())) {
                    throw new IllegalStateException("추천 검색어 중복 항목 — " + keyword.asText());
                }
                loaded.add(new RecommendedSearchKeywordResponse(keyword.asText(), resultCount.asInt()));
            }
            return List.copyOf(loaded);
        } catch (IOException ex) {
            throw new IllegalStateException("추천 검색어 리소스 읽기 실패: " + RESOURCE, ex);
        }
    }
}
