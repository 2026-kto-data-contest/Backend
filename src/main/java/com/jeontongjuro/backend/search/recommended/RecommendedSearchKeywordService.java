package com.jeontongjuro.backend.search.recommended;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.search.SearchKeyword;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검색 화면 추천 검색어 후보를 배포 리소스에서 읽고, 현재 검색 결과 수로 필터링해 제공한다. */
@Service
@Transactional(readOnly = true)
public class RecommendedSearchKeywordService {

    private static final String RESOURCE = "/recommended_search_keywords.json";

    private static final int MIN_RESULT_COUNT = 5;

    private final BreweryQueryService breweryQueryService;
    private final List<String> candidateKeywords;

    public RecommendedSearchKeywordService(ObjectMapper objectMapper, BreweryQueryService breweryQueryService) {
        this.breweryQueryService = breweryQueryService;
        this.candidateKeywords = load(objectMapper);
    }

    public List<RecommendedSearchKeywordResponse> list() {
        List<String> normalizedKeywords = candidateKeywords.stream()
                .map(SearchKeyword::normalizeForMatch)
                .toList();
        Map<String, Long> counts = breweryQueryService.countByAccuracy(normalizedKeywords);
        List<RecommendedSearchKeywordResponse> result = new ArrayList<>();
        for (String keyword : candidateKeywords) {
            long resultCount = counts.getOrDefault(SearchKeyword.normalizeForMatch(keyword), 0L);
            if (resultCount >= MIN_RESULT_COUNT) {
                result.add(new RecommendedSearchKeywordResponse(keyword, resultCount));
            }
        }
        return List.copyOf(result);
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
