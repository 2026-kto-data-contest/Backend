package com.jeontongjuro.backend.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeontongjuro.backend.drift.BreweryRawDiffer.ChangedEntry;
import com.jeontongjuro.backend.drift.BreweryRawDiffer.DiffResult;
import com.jeontongjuro.backend.drift.BreweryRawDiffer.FieldChange;
import com.jeontongjuro.backend.drift.BreweryRawDiffer.RenameHint;
import com.jeontongjuro.backend.drift.BreweryRawDiffer.ReorderedEntry;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 8/8 새 스냅샷 도착 시 20260728 골든과 비교해 무엇이 바뀌었는지 검증하는 운영 점검 도구 테스트.
 * 20260728 골든 위 합성 변형(flip/add/remove/shuffle)으로 "가짜 8/8"을 만들어 4버킷 분류를 검증한다.
 */
class BreweryRawDifferTest {

    private static final String GOLDEN_PATH = "/golden/20260728_brewery_raw.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode golden;

    @BeforeEach
    void loadGolden() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(GOLDEN_PATH)) {
            assertThat(in).as("golden 리소스 존재: %s", GOLDEN_PATH).isNotNull();
            golden = objectMapper.readTree(in);
        }
    }

    @Test
    @DisplayName("flip: 특정 상호명의 특정 필드만 바뀌면 changed에 정확히 1건, 나머지 버킷은 비어야 함")
    void flipProducesSingleChangedEntry() {
        JsonNode next = BreweryRawFixtures.flip(golden, "배상면주가", "주소", "경기도 포천시 화현면 화현리 999-9");

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
        assertThat(result.changed()).hasSize(1);

        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.name()).isEqualTo("배상면주가");
        assertThat(entry.changes()).containsExactly(
                new FieldChange("주소", "경기도 포천시 화현면 화현리 512", "경기도 포천시 화현면 화현리 999-9"));
    }

    @Test
    @DisplayName("add: 맨 끝에 신규 상호명 1행이 추가되면 added에 1건, 나머지 버킷에 영향 없어야 함")
    void addProducesSingleAddedEntry() {
        ObjectNode newRow = objectMapper.createObjectNode();
        newRow.put("사용여부", "Y");
        newRow.put("상시방문가능여부", "Y");
        newRow.put("상호명", "테스트신규양조장");
        newRow.put("예약방문가능여부", "Y");
        newRow.put("조회수", 0);
        newRow.put("주소", "서울 강남구 테스트로 1");
        newRow.putNull("홈페이지");

        JsonNode next = BreweryRawFixtures.add(golden, newRow);

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.added()).containsExactly("테스트신규양조장");
        assertThat(result.removed()).isEmpty();
        assertThat(result.changed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("remove: 중간 1행 삭제 시 removed 1건, 그 아래 행들은 changed로 오탐되지 않고 reordered로만 잡혀야 함")
    void removeShiftsLowerRowsWithoutFalseChanged() {
        String removedName = "양촌양조"; // golden data[10]
        int removedIndex = 10;
        int totalRows = golden.get("data").size();

        JsonNode next = BreweryRawFixtures.remove(golden, removedName);

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.removed()).containsExactly(removedName);
        assertThat(result.added()).isEmpty();
        assertThat(result.changed()).isEmpty();

        int expectedShiftedRows = totalRows - 1 - removedIndex;
        assertThat(result.reordered()).hasSize(expectedShiftedRows);
        assertThat(result.reordered())
                .allSatisfy(r -> assertThat(r.oldIndex()).isEqualTo(r.newIndex() + 1));
    }

    @Test
    @DisplayName("shuffle: 값은 그대로 배열 순서만 섞이면 전부 reordered, changed/added/removed는 0이어야 함(재정렬 오탐 방지)")
    void shuffleProducesOnlyReordered() {
        int totalRows = golden.get("data").size();

        JsonNode next = BreweryRawFixtures.shuffle(golden);

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.changed()).isEmpty();
        assertThat(result.reordered()).hasSize(totalRows);
        assertThat(result.reordered()).extracting(ReorderedEntry::oldIndex).doesNotContainNull();
    }

    @Test
    @DisplayName("null→값: null이던 필드가 값으로 채워지면 changed(before=null, after=값)로 정확히 잡혀야 함")
    void nullFilledInIsDetectedAsChanged() {
        // golden data[0] "산머루농원"의 "상시방문가능여부"는 null
        JsonNode next = BreweryRawFixtures.flip(golden, "산머루농원", "상시방문가능여부", "Y");

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.changed()).hasSize(1);
        ChangedEntry entry = result.changed().get(0);
        assertThat(entry.name()).isEqualTo("산머루농원");
        assertThat(entry.changes()).containsExactly(new FieldChange("상시방문가능여부", null, "Y"));
        assertThat(result.added()).isEmpty();
        assertThat(result.removed()).isEmpty();
        assertThat(result.reordered()).isEmpty();
    }

    @Test
    @DisplayName("동명 업체가 한쪽 스냅샷 안에 2건 이상 있으면 조용히 임의 매칭하지 않고 즉시 실패해야 함")
    void duplicateNameWithinSnapshotFailsLoudly() {
        ObjectNode oldRoot = objectMapper.createObjectNode();
        ArrayNode oldData = oldRoot.putArray("data");
        oldData.addObject().put("상호명", "중복양조장");
        oldData.addObject().put("상호명", "중복양조장");

        ObjectNode newRoot = objectMapper.createObjectNode();
        newRoot.putArray("data").addObject().put("상호명", "중복양조장");

        assertThatThrownBy(() -> BreweryRawDiffer.diff(oldRoot, newRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("중복양조장");
    }

    @Test
    @DisplayName("개명 힌트: removed·added 쌍의 주소가 일치하면 힌트로 병기하되 자동으로 same-entity 병합하지 않음")
    void renameHintSuggestsPairWithoutAutoMerging() {
        String removedName = "양촌양조"; // golden data[10], 주소: 충남 논산시 양촌면 매죽헌로1665번길 14-9
        String removedAddress = golden.get("data").get(10).get("주소").asText();

        JsonNode removed = BreweryRawFixtures.remove(golden, removedName);

        ObjectNode renamedRow = objectMapper.createObjectNode();
        renamedRow.put("사용여부", "Y");
        renamedRow.put("상시방문가능여부", "Y");
        renamedRow.put("상호명", "양촌양조(신규상호)");
        renamedRow.put("예약방문가능여부", "Y");
        renamedRow.put("조회수", 0);
        renamedRow.put("주소", removedAddress);
        renamedRow.putNull("홈페이지");
        JsonNode next = BreweryRawFixtures.add(removed, renamedRow);

        DiffResult result = BreweryRawDiffer.diff(golden, next);

        assertThat(result.removed()).containsExactly(removedName);
        assertThat(result.added()).containsExactly("양촌양조(신규상호)");
        assertThat(result.renameHints()).containsExactly(
                new RenameHint(removedName, "양촌양조(신규상호)", removedAddress));
    }
}
