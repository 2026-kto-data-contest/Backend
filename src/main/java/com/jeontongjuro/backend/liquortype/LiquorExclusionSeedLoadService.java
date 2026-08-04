package com.jeontongjuro.backend.liquortype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 주종 오탐 제외 시드 로더(3d, 이슈 #20 3차). {@code src/main/resources/liquor_exclusion_seed.json}을
 * 읽어 인메모리 {@link ExclusionKey} Set으로 노출한다. ★DB 테이블을 신설하지 않는다 — 결과를 어디에도 쓰지 않고
 * {@link LiquorInferenceService#infer}가 AUTO 삽입 전 필터로만 쓴다.
 * <p>
 * {@link LiquorManualSeedLoadService}와 같은 JSON 파싱 패턴을 재사용하되, 참조 대상이 DB 삽입이 아니라
 * 삽입 억제이므로 breweryId 도출·저장 로직이 없다.
 */
@Service
public class LiquorExclusionSeedLoadService {

    private static final String SEED_CLASSPATH = "/liquor_exclusion_seed.json";

    private final ObjectMapper objectMapper;

    public LiquorExclusionSeedLoadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 제외 판정의 식별 키. (source_row_ref, liquorType) 쌍 단위 — ref만으로 비교하면 안 된다(같은 ref에 다른 주종 정탐이 공존 가능). */
    public record ExclusionKey(Integer sourceRowRef, LiquorType liquorType) {
    }

    /** 근거(reason)를 포함한 전건 — 감사 리포트 노출용. */
    public record ExclusionEntry(Integer sourceRowRef, LiquorType liquorType, String reason) {
        public ExclusionKey key() {
            return new ExclusionKey(sourceRowRef, liquorType);
        }
    }

    /** AUTO 삽입 전 필터로 쓸 제외 키 Set. */
    public Set<ExclusionKey> load() {
        Set<ExclusionKey> keys = new LinkedHashSet<>();
        for (ExclusionEntry e : loadEntries()) {
            keys.add(e.key());
        }
        return keys;
    }

    /** reason을 포함한 전건(감사 리포트 전용). */
    public List<ExclusionEntry> loadEntries() {
        JsonNode entries = readSeedEntries();
        List<ExclusionEntry> parsed = new ArrayList<>();
        for (JsonNode e : entries) {
            int rowRef = e.get("source_row_ref").asInt();
            LiquorType type = LiquorType.valueOf(e.get("liquorType").asText());
            String reason = e.get("reason").asText();
            parsed.add(new ExclusionEntry(rowRef, type, reason));
        }
        return parsed;
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("주종 EXCLUSION 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("주종 EXCLUSION 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("주종 EXCLUSION 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
