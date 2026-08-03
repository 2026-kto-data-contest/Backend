package com.jeontongjuro.backend.liquortype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 주종 키워드 사전 로더. {@code src/main/resources/liquor_keyword_seed.json}을 읽어
 * 키워드 규칙 목록과 향미어 목록으로 노출한다. ★추론 규칙은 전부 JSON이 결정하고 이 클래스는 파싱만 한다
 * (자바 코드에 단어를 하드코딩하지 않는다).
 * <p>
 * 부팅 시 1회 로드해 불변 리스트로 보관한다(59행 규모 · 소규모 사전이라 재읽기 불필요).
 */
@Component
public class LiquorKeywordDictionary {

    private static final String SEED_CLASSPATH = "/liquor_keyword_seed.json";

    private final List<LiquorKeyword> keywords;
    private final List<String> flavorWords;

    public LiquorKeywordDictionary(ObjectMapper objectMapper) {
        JsonNode root = readRoot(objectMapper);
        this.keywords = parseKeywords(root.get("keywords"));
        this.flavorWords = parseFlavorWords(root.get("flavor_words"));
    }

    /** 사전 순서를 보존한 불변 키워드 목록(첫 매칭 키워드를 근거로 기록하므로 순서가 의미 있다). */
    public List<LiquorKeyword> keywords() {
        return keywords;
    }

    /** C-4 향미이중 억제 판정용 향미어 목록(주종 키워드가 아님). */
    public List<String> flavorWords() {
        return flavorWords;
    }

    private JsonNode readRoot(ObjectMapper objectMapper) {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("주종 키워드 사전 리소스 없음: " + SEED_CLASSPATH);
            }
            return objectMapper.readTree(in);
        } catch (IOException ex) {
            throw new IllegalStateException("주종 키워드 사전 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }

    private List<LiquorKeyword> parseKeywords(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("주종 키워드 사전 형식 오류: keywords 배열 없음 — " + SEED_CLASSPATH);
        }
        List<LiquorKeyword> parsed = new ArrayList<>();
        for (JsonNode e : node) {
            String keyword = e.get("keyword").asText();
            LiquorType liquorType = LiquorType.valueOf(e.get("liquorType").asText());
            Set<String> scope = new LinkedHashSet<>();
            for (JsonNode s : e.get("scope")) {
                scope.add(s.asText());
            }
            boolean wordBoundary = e.path("wordBoundary").asBoolean(false);
            parsed.add(new LiquorKeyword(keyword, liquorType, Set.copyOf(scope), wordBoundary));
        }
        return List.copyOf(parsed);
    }

    private List<String> parseFlavorWords(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("주종 키워드 사전 형식 오류: flavor_words 배열 없음 — " + SEED_CLASSPATH);
        }
        List<String> parsed = new ArrayList<>();
        for (JsonNode s : node) {
            parsed.add(s.asText());
        }
        return List.copyOf(parsed);
    }
}
