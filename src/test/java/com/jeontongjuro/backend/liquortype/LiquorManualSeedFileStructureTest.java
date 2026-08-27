package com.jeontongjuro.backend.liquortype;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주종 수동 판정 시드(liquor_manual_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (ProductExclusionSeedStructureTest 선례).
 * <p>
 * ★{@code basis} 키는 <b>선택</b>이다 — 29행은 basis가 없고(1~2차분), 192행은 있다(3차 batch3에서 추가,
 * {@code _meta.batch3_note} 참고). 따라서 화이트리스트는 {@code containsExactlyInAnyOrderElementsOf}가
 * 아니라 <b>부분집합(⊆ 5개 허용) AND 필수 4개 포함</b>으로 검사한다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code entries}이고 221행, basis 보유 192행 / 미보유 29행.</li>
 *   <li>각 항목은 필수 4키(source_row_ref·liquorType·reason·decided_at) + 선택 1키(basis)만 가지며,
 *       source_row_ref는 정수·중복 없음, liquorType은 {@link LiquorType} 6종 중 하나.</li>
 * </ol>
 */
class LiquorManualSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** entries 필수 키(4개 — 모든 행에 있어야 함). */
    private static final Set<String> REQUIRED = Set.of("source_row_ref", "liquorType", "reason", "decided_at");

    /** entries 허용 키(필수 4개 + 선택 basis). */
    private static final Set<String> ALLOWED = Set.of(
            "source_row_ref", "liquorType", "reason", "decided_at", "basis");

    @Test
    @DisplayName("주종 수동 판정 시드: entries 221행(basis 보유 192·미보유 29) · 키는 필수4+선택1(basis) · "
            + "source_row_ref 정수·중복 없음 · liquorType은 6종 중 하나")
    void liquorManualSeedShape() throws Exception {
        JsonNode entries = readEntries("/liquor_manual_seed.json");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(221);

        List<Integer> refs = new ArrayList<>();
        int withBasis = 0;
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 필수4+선택(basis) 안에서만: %s", keys)
                    .isSubsetOf(ALLOWED);
            assertThat(keys).as("entries 키는 필수 4개를 반드시 포함: %s", keys)
                    .containsAll(REQUIRED);

            assertThat(e.get("source_row_ref").isInt()).as("source_row_ref는 정수").isTrue();
            assertThat(e.get("liquorType").asText()).as("liquorType은 LiquorType 6종 중 하나")
                    .isIn("탁주", "약주", "청주", "증류주", "과실주", "기타");
            assertThat(e.get("reason").asText()).as("reason 비어있지 않음").isNotBlank();
            assertThat(e.get("decided_at").asText()).as("decided_at 비어있지 않음").isNotBlank();
            if (e.has("basis")) {
                withBasis++;
                assertThat(e.get("basis").asText()).as("basis 비어있지 않음").isNotBlank();
            }
            refs.add(e.get("source_row_ref").asInt());
        }
        assertThat(refs).as("source_row_ref 중복 없음").doesNotHaveDuplicates();
        assertThat(withBasis).as("basis 보유 192행").isEqualTo(192);
    }

    private JsonNode readEntries(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode entries = objectMapper.readTree(in).get("entries");
            assertThat(entries).as("entries 배열").isNotNull();
            return entries;
        }
    }
}
