package com.jeontongjuro.backend.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeontongjuro.backend.drift.ProductRawDiffer.ChangedEntry;
import com.jeontongjuro.backend.drift.ProductRawDiffer.DiffResult;
import com.jeontongjuro.backend.drift.ProductRawDiffer.FieldChange;
import com.jeontongjuro.backend.drift.ProductRawDiffer.GroupChangedEntry;
import com.jeontongjuro.backend.drift.ProductRawDiffer.NaturalKey;
import com.jeontongjuro.backend.drift.ProductRawDiffer.ReorderedEntry;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 8/8 새 product 스냅샷 도착 시 20260728 골든과 비교해 무엇이 바뀌었는지 검증하는 운영 점검 도구 테스트.
 * 20260728 골든 위 합성 변형(flip/add/remove/shuffle)으로 "가짜 8/8"을 만들어 5버킷 분류를 검증한다.
 *
 * <p>인덱스·키 상수는 착수 시 골든으로 재확인한 실측값이다(제품명 유일성이 없어 이름 대신 인덱스로 지정).
 */
class ProductRawDifferTest {

    private static final String GOLDEN_PATH = "/golden/20260728_product_raw.json";

    /** 1:1 유일 키 표본. data[0] = 이동 생 쌀 막걸리 / 이동주조 / 6 / 750ml, 수상경력=null. */
    private static final int IDX_UNIQUE = 0;
    /** 필/술아원/25/375ml 그룹(count=2)의 두 행. */
    private static final int IDX_GROUP_A = 455;
    /** 라봉 / 양조장=null / 5.5 / 650ml (778 라봉/다도참주가와 키가 달라 1:1). */
    private static final int IDX_NULL_BREWERY = 1174;
    /** 솔청정막걸리 / 조선양조 / 6 / 용량=null (1:1). */
    private static final int IDX_NULL_VOLUME = 174;
    /** 청명주 / 중원당 / 17 / 500ml — 동명 3행이나 4필드까지 갈라지는 1:1. */
    private static final int IDX_DUP_NAME = 271;

    /**
     * 4필드 복합키 잔여충돌 = 5그룹 × 2행 = 10행(전부 count=2).
     * 필/술아원, 메로니아/배혜정도가, 도구막걸리/동해명주, 대잎술/추성고을, 공주애 오디와인/사곡양조원.
     * 이 10행은 그룹 경로라 reordered를 방출하지 않는다.
     */
    private static final int GROUP_MEMBER_ROWS = 10;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode golden;

    @BeforeEach
    void loadGolden() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(GOLDEN_PATH)) {
            assertThat(in).as("golden 리소스 존재: %s", GOLDEN_PATH).isNotNull();
            golden = objectMapper.readTree(in);
        }
    }

    private JsonNode row(int index) {
        return golden.get("data").get(index);
    }

    private NaturalKey keyOf(int index) {
        JsonNode r = row(index);
        return new NaturalKey(
                text(r, "제품명"), text(r, "양조장"), text(r, "알콜도수"), text(r, "용량"));
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    @Test
    @DisplayName("flip: 1:1 유일 키의 non-key 필드 1개만 바뀌면 changed 정확히 1건, 나머지 버킷은 비어야 함")
    void flipProducesSingleChangedEntry() {
        String before = text(row(IDX_UNIQUE), "성분");
        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_UNIQUE, "성분", "테스트-성분-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
        assertThat(result.changed()).hasSize(1);

        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.key()).isEqualTo(keyOf(IDX_UNIQUE));
        assertThat(entry.changes())
                .containsExactly(new FieldChange("성분", before, "테스트-성분-변경"));
    }

    @Test
    @DisplayName("add: 신규 4필드 조합 1행이 추가되면 added에 1건, 나머지 버킷에 영향 없어야 함")
    void addProducesSingleAddedEntry() {
        ObjectNode newRow = ProductRawFixtures.copyRow(golden, IDX_UNIQUE);
        newRow.put("제품명", "테스트-신규제품-고유"); // 나머지 3필드는 그대로 → 새 유일 키
        NaturalKey addedKey = new NaturalKey("테스트-신규제품-고유",
                text(row(IDX_UNIQUE), "양조장"), text(row(IDX_UNIQUE), "알콜도수"), text(row(IDX_UNIQUE), "용량"));

        JsonNode next = ProductRawFixtures.add(golden, newRow);

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.added()).containsExactly(addedKey);
        assertThat(result.removed()).isEmpty();
        assertThat(result.changed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("★remove(differ의 심장): 중간 1행 삭제 → removed 1건, changed=0·groupChanged=0, 아래 밀린 행은 전부 reordered")
    void removeShiftsLowerRowsWithoutFalseChanged() {
        int totalRows = golden.get("data").size(); // 1215
        NaturalKey removedKey = keyOf(IDX_NULL_BREWERY);

        JsonNode next = ProductRawFixtures.removeAt(golden, IDX_NULL_BREWERY);

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.removed()).containsExactly(removedKey);
        assertThat(result.added()).isEmpty();
        // 최엄격 계약: 위치가 밀렸다고 changed/groupChanged가 단 1건이라도 나오면 differ 전체가 무의미하다.
        assertThat(result.changed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();

        // IDX_NULL_BREWERY(1174) 아래 행(1175..1214)은 전부 1:1 유일 키(그룹 멤버 최대 인덱스=1024 < 1174).
        int expectedShifted = totalRows - 1 - IDX_NULL_BREWERY; // 40
        assertThat(result.reordered()).hasSize(expectedShifted);
        assertThat(result.reordered())
                .allSatisfy(r -> assertThat(r.oldIndex()).isEqualTo(r.newIndex() + 1));
    }

    @Test
    @DisplayName("shuffle: 값 그대로 순서만 섞이면 1:1 행은 전부 reordered, 그룹 10행은 무보고, changed/added/removed/groupChanged=0")
    void shuffleProducesOnlyReordered() {
        int totalRows = golden.get("data").size();

        JsonNode next = ProductRawFixtures.shuffle(golden);

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.changed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        // 그룹 10행은 rotate 후에도 같은 키 size=2·내용 동일 → 그룹 경로라 reordered 방출 안 함.
        assertThat(result.reordered()).hasSize(totalRows - GROUP_MEMBER_ROWS);
        assertThat(result.reordered())
                .allSatisfy(r -> assertThat(r.oldIndex()).isNotEqualTo(r.newIndex()));
    }

    @Test
    @DisplayName("null→값(non-key 필드): null이던 수상경력이 값으로 채워지면 changed(before=null, after=값)")
    void nullFilledInIsDetectedAsChanged() {
        assertThat(text(row(IDX_UNIQUE), "수상경력")).as("전제: data[0] 수상경력=null").isNull();

        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_UNIQUE, "수상경력", "2020 대한민국우리술품평회 대상");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.changed()).hasSize(1);
        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.key()).isEqualTo(keyOf(IDX_UNIQUE));
        assertThat(entry.changes())
                .containsExactly(new FieldChange("수상경력", null, "2020 대한민국우리술품평회 대상"));
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("그룹 내용변화·크기불변: 그룹 한 행의 성분만 바뀌면 groupChanged(sizeChanged=false) 1건, changed엔 개별 FieldChange 안 붙음")
    void groupContentChangeReportedAtGroupLevel() {
        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_GROUP_A, "성분", "그룹-내-성분-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.groupChanged()).hasSize(1);
        GroupChangedEntry entry = result.groupChanged().get(0);
        assertThat(entry.key()).isEqualTo(keyOf(IDX_GROUP_A)); // 필/술아원/25/375ml
        assertThat(entry.oldSize()).isEqualTo(2);
        assertThat(entry.newSize()).isEqualTo(2);
        assertThat(entry.sizeChanged()).isFalse();

        // 그룹 경로와 1:1 경로 상호 비침투: 어느 행이 바뀌었는지 changed로 새어나오면 안 됨.
        assertThat(result.changed()).isEmpty();
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("그룹 크기변화(2→3): 동일 키 3번째 행 추가 → groupChanged(sizeChanged=true), added/changed로 안 샘")
    void groupSizeChangeReportedAtGroupLevel() {
        ObjectNode thirdRow = ProductRawFixtures.copyRow(golden, IDX_GROUP_A); // 동일 키(필/술아원/25/375ml)
        JsonNode next = ProductRawFixtures.add(golden, thirdRow);

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.groupChanged()).hasSize(1);
        GroupChangedEntry entry = result.groupChanged().get(0);
        assertThat(entry.key()).isEqualTo(keyOf(IDX_GROUP_A));
        assertThat(entry.oldSize()).isEqualTo(2);
        assertThat(entry.newSize()).isEqualTo(3);
        assertThat(entry.sizeChanged()).isTrue();

        assertThat(result.added()).isEmpty(); // 키는 old에 이미 존재 → added 아님
        assertThat(result.changed()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("null 행 changed: 양조장=null 행의 non-key 필드만 바뀌면 changed 1건, 키의 양조장 컴포넌트는 null 유지")
    void nullBreweryRowChangeKeepsNullInKey() {
        assertThat(keyOf(IDX_NULL_BREWERY).brewery()).as("전제: 라봉 양조장=null").isNull();

        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_NULL_BREWERY, "성분", "라봉-성분-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.changed()).hasSize(1);
        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.key().name()).isEqualTo("라봉");
        assertThat(entry.key().brewery()).isNull();
        assertThat(entry.changes()).extracting(FieldChange::field).containsExactly("성분");
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("★null→값 전환 회귀 고정: 양조장을 null→실값으로 채우면 changed가 아니라 removed(구키)+added(신키) 쌍으로 나와야 함")
    void nullBreweryFilledInBecomesRemovePlusAdd() {
        NaturalKey oldKey = keyOf(IDX_NULL_BREWERY); // (라봉, null, 5.5, 650ml)
        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_NULL_BREWERY, "양조장", "라봉전용양조장");
        NaturalKey newKey = new NaturalKey("라봉", "라봉전용양조장", oldKey.abv(), oldKey.volume());

        DiffResult result = ProductRawDiffer.diff(golden, next);

        // 방침1의 필연적 결과 — 의도된 동작임을 못박아 훗날 "고쳐서" 방침 깨는 회귀를 차단.
        assertThat(result.removed()).containsExactly(oldKey);
        assertThat(result.added()).containsExactly(newKey);
        assertThat(result.changed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("동명 비충돌 오염검사: 4필드까지 갈라지는 동명 3행(청명주) 중 하나만 바뀌면 그 1:1 키만 changed, 상호 간섭·그룹화 없음")
    void duplicateNameButDistinctKeysDoNotInterfere() {
        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_DUP_NAME, "성분", "청명주-성분-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.changed()).hasSize(1);
        assertThat(result.changed().get(0).key()).isEqualTo(keyOf(IDX_DUP_NAME)); // 청명주/중원당/17/500ml
        // 동명 가드 제거 부작용 없음: 나머지 청명주 행은 독립 1:1로 남고 그룹화되지 않는다.
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("용량 결측 1건: 용량=null인 키의 non-key 필드가 바뀌어도 정상 changed(키의 용량 컴포넌트 null 유지)")
    void nullVolumeRowChangeIsDetected() {
        assertThat(keyOf(IDX_NULL_VOLUME).volume()).as("전제: 솔청정막걸리 용량=null").isNull();

        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_NULL_VOLUME, "성분", "솔청정-성분-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.changed()).hasSize(1);
        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.key().name()).isEqualTo("솔청정막걸리");
        assertThat(entry.key().volume()).isNull();
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.groupChanged()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("summaryLine: 규모 요약 한 줄이 각 버킷 크기를 그대로 반영해야 함")
    void summaryLineReflectsBucketSizes() {
        JsonNode next = ProductRawFixtures.flipAt(golden, IDX_UNIQUE, "성분", "요약-검증-변경");

        DiffResult result = ProductRawDiffer.diff(golden, next);

        assertThat(result.summaryLine())
                .isEqualTo("added=0 removed=0 changed=1 groupChanged=0 reordered=0 volatileOnly=0");
    }
}
