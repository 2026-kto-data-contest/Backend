package com.jeontongjuro.backend.product.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 제품 목록 조회의 원본 오류 제외 시드 로더. {@code src/main/resources/product_exclusion_seed.json}을
 * 기동 시 한 번 읽어 인메모리 {@code Set<Integer>}(제외할 source_row_index)로 노출한다.
 * <p>
 * ★DB 테이블·컬럼을 신설하지 않는다 — {@link ProductQueryService}가 파이프라인 ③ 단계(판매중단 제외 후,
 * 중복 병합 전)에서 제외 필터로만 쓴다. 삭제가 아니라 필터라 멱등하다.
 * {@code liquortype/LiquorExclusionSeedLoadService}와 같은 JSON 파싱 패턴을 따른다.
 */
@Component
public class ProductExclusionSeed {

    private static final String SEED_CLASSPATH = "/product_exclusion_seed.json";

    /** 기동 시 1회 로드해 고정(리소스는 배포 산출물이라 런타임 변경 없음). */
    private final Set<Integer> excludedRowIndexes;

    public ProductExclusionSeed(ObjectMapper objectMapper) {
        this.excludedRowIndexes = load(objectMapper);
    }

    /** 이 source_row_index가 원본 오류로 제외 대상인지. */
    public boolean isExcluded(Integer sourceRowIndex) {
        return sourceRowIndex != null && excludedRowIndexes.contains(sourceRowIndex);
    }

    /** 제외 대상 전체(읽기 전용 사본). 감사·테스트용. */
    public Set<Integer> excludedRowIndexes() {
        return Set.copyOf(excludedRowIndexes);
    }

    private static Set<Integer> load(ObjectMapper objectMapper) {
        try (InputStream in = ProductExclusionSeed.class.getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("제품 제외 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("제품 제외 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            Set<Integer> refs = new LinkedHashSet<>();
            for (JsonNode e : entries) {
                JsonNode ref = e.get("source_row_index");
                if (ref == null || !ref.canConvertToInt()) {
                    throw new IllegalStateException("제품 제외 시드 항목에 정수 source_row_index 없음 — " + SEED_CLASSPATH);
                }
                refs.add(ref.asInt());
            }
            return refs;
        } catch (IOException ex) {
            throw new IllegalStateException("제품 제외 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
