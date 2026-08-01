package com.jeontongjuro.backend.drift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 8/8 product raw 스냅샷과 20260728(이전) 스냅샷을 비교해
 * added/removed/changed/groupChanged/reordered 5버킷으로 분류하는 순수 Jackson 기반 운영 점검 도구.
 * DB·Spring·brewery/product/override/pipeline 패키지에 의존하지 않는다.
 *
 * <p>brewery differ와 갈라지는 3지점(복붙 금지):
 * <ol>
 *   <li>자연키가 상호명 1필드가 아니라 (제품명·양조장·알콜도수·용량) 4필드 복합키다. 양조장=null도 정상 유효값.</li>
 *   <li>brewery의 "동명 즉시 예외(IllegalStateException)" 가드가 없다. 동일 키 2행 이상은 정상이며 그룹 경로로 분기한다.</li>
 *   <li>위치(source_row_index)는 키가 아니라 1:1 매칭된 키의 reordered 보고용 부가속성으로만 쓴다.
 *       그룹(size&gt;1) 키는 행별 정체성이 없으므로 위치 비교·내부 짝짓기를 하지 않는다.</li>
 * </ol>
 */
public final class ProductRawDiffer {

    /**
     * 자연키 필드. raw 원본 문자열을 그대로 비교하며 정규화·trim·소문자화·표기통일을 일절 하지 않는다.
     * 이유: 표기 변화("750ml"→"375ml"), 오타 교정("375nl"→"375ml"), 수치 표기("12"→"12.0")도
     * differ가 잡아야 할 실제 변경이다. 여기서 정규화하면 진짜 변경을 놓친다.
     */
    public static final List<String> KEY_FIELDS = List.of("제품명", "양조장", "알콜도수", "용량");

    /**
     * changed 판정에서 제외하는 자연 변동 필드. product_raw엔 조회수 같은 자동증가 카운터가 없어 비어 있다.
     * 판매여부(Y/N)는 재고소진·단종일 수 있는 상태신호라 volatile로 빼지 않고 changed에 남긴다.
     * TODO(8/8): 스냅샷 2장 비교 후 판매여부가 실제로 노이즈처럼 흔들리면 volatile 편입 재검토.
     */
    public static final Set<String> VOLATILE_FIELDS = Set.of();

    /** canonicalRow 직렬화 구분자 — 원본 텍스트에 등장하지 않는 제어문자. */
    private static final char UNIT_SEP = '\u001F';
    private static final char KV_SEP = '\u001E';

    private ProductRawDiffer() {
    }

    /** 4필드 복합 자연키. 양조장 등 어떤 컴포넌트도 null일 수 있다(record equals/hashCode가 null 처리). */
    public record NaturalKey(String name, String brewery, String abv, String volume) {
    }

    public record FieldChange(String field, String before, String after) {
    }

    /** 1:1로 유일하게 매칭된 키의 필드 변경. oldIndex/newIndex는 보고용 참고값일 뿐 매칭 근거가 아니다. */
    public record ChangedEntry(NaturalKey key, int oldIndex, int newIndex, List<FieldChange> changes) {
    }

    /** 1:1 매칭된 키의 위치만 바뀐 경우. */
    public record ReorderedEntry(NaturalKey key, int oldIndex, int newIndex) {
    }

    /**
     * 동일 키가 2행 이상인 그룹의 변화. brewery엔 없는 신설 버킷.
     * 그룹 내부 행은 개별 식별하지 않으므로 어느 행이 바뀌었는지는 특정하지 않는다(위치로 짝짓지 않는다).
     * sizeChanged=true면 행 수 증감(예: 2→3, 2→1), false면 행 수는 같고 내용 multiset이 달라짐.
     */
    public record GroupChangedEntry(NaturalKey key, int oldSize, int newSize, boolean sizeChanged) {
    }

    public record DiffResult(
            List<NaturalKey> added,
            List<NaturalKey> removed,
            List<ChangedEntry> changed,
            List<GroupChangedEntry> groupChanged,
            List<ReorderedEntry> reordered,
            int volatileOnlyCount
    ) {
        /** 8/8 당일 리스트 전체를 훑기 전에 규모를 즉시 파악하는 요약 한 줄. */
        public String summaryLine() {
            return "added=" + added.size()
                    + " removed=" + removed.size()
                    + " changed=" + changed.size()
                    + " groupChanged=" + groupChanged.size()
                    + " reordered=" + reordered.size()
                    + " volatileOnly=" + volatileOnlyCount;
        }
    }

    public static DiffResult diff(JsonNode oldRoot, JsonNode newRoot) {
        ArrayNode oldRows = (ArrayNode) oldRoot.get("data");
        ArrayNode newRows = (ArrayNode) newRoot.get("data");

        Map<NaturalKey, List<Integer>> oldGroups = groupByKey(oldRows);
        Map<NaturalKey, List<Integer>> newGroups = groupByKey(newRows);

        List<NaturalKey> added = new ArrayList<>();
        List<NaturalKey> removed = new ArrayList<>();
        List<ChangedEntry> changed = new ArrayList<>();
        List<GroupChangedEntry> groupChanged = new ArrayList<>();
        List<ReorderedEntry> reordered = new ArrayList<>();
        int volatileOnlyCount = 0;

        for (NaturalKey key : newGroups.keySet()) {
            if (!oldGroups.containsKey(key)) {
                added.add(key);
            }
        }
        for (NaturalKey key : oldGroups.keySet()) {
            if (!newGroups.containsKey(key)) {
                removed.add(key);
            }
        }

        for (Map.Entry<NaturalKey, List<Integer>> entry : oldGroups.entrySet()) {
            NaturalKey key = entry.getKey();
            List<Integer> oldIdxs = entry.getValue();
            List<Integer> newIdxs = newGroups.get(key);
            if (newIdxs == null) {
                continue;
            }

            if (oldIdxs.size() == 1 && newIdxs.size() == 1) {
                int oldIdx = oldIdxs.get(0);
                int newIdx = newIdxs.get(0);
                JsonNode oldRow = oldRows.get(oldIdx);
                JsonNode newRow = newRows.get(newIdx);

                List<FieldChange> fieldChanges = compareNonVolatileFields(oldRow, newRow);
                boolean volatileDiffers =
                        VOLATILE_FIELDS.stream().anyMatch(field -> fieldDiffers(oldRow, newRow, field));

                if (!fieldChanges.isEmpty()) {
                    changed.add(new ChangedEntry(key, oldIdx, newIdx, fieldChanges));
                } else if (oldIdx != newIdx) {
                    reordered.add(new ReorderedEntry(key, oldIdx, newIdx));
                } else if (volatileDiffers) {
                    volatileOnlyCount++;
                }
            } else {
                // 그룹 경로: 개별 행 특정 없이 그룹 단위로만 비교. 위치는 전혀 보지 않는다.
                if (oldIdxs.size() != newIdxs.size()) {
                    groupChanged.add(new GroupChangedEntry(key, oldIdxs.size(), newIdxs.size(), true));
                } else if (!groupRepr(oldRows, oldIdxs).equals(groupRepr(newRows, newIdxs))) {
                    groupChanged.add(new GroupChangedEntry(key, oldIdxs.size(), newIdxs.size(), false));
                }
            }
        }

        added.sort(KEY_COMPARATOR);
        removed.sort(KEY_COMPARATOR);

        return new DiffResult(added, removed, changed, groupChanged, reordered, volatileOnlyCount);
    }

    /** 자연키 → 등장 인덱스 목록. size&gt;=2도 정상 반환하며 예외를 던지지 않는다(brewery 가드와 결정적 차이). */
    private static Map<NaturalKey, List<Integer>> groupByKey(ArrayNode rows) {
        Map<NaturalKey, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            NaturalKey key = new NaturalKey(
                    asNullableText(row.get("제품명")),
                    asNullableText(row.get("양조장")),
                    asNullableText(row.get("알콜도수")),
                    asNullableText(row.get("용량")));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    private static List<FieldChange> compareNonVolatileFields(JsonNode oldRow, JsonNode newRow) {
        TreeSet<String> fieldNames = new TreeSet<>();
        oldRow.fieldNames().forEachRemaining(fieldNames::add);
        newRow.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.removeAll(KEY_FIELDS);
        fieldNames.removeAll(VOLATILE_FIELDS);

        List<FieldChange> changes = new ArrayList<>();
        for (String field : fieldNames) {
            String before = asNullableText(oldRow.get(field));
            String after = asNullableText(newRow.get(field));
            if (!Objects.equals(before, after)) {
                changes.add(new FieldChange(field, before, after));
            }
        }
        return changes;
    }

    /**
     * 그룹의 위치-무관 결정적 표현. 각 행을 (비-volatile 필드만, 필드명 정렬) 직렬화한 뒤 그 목록을 정렬한 multiset.
     * source_row_index(위치)·원본 JSON 필드 순서·toString에 의존하지 않는다 — 행이 밀려도 오탐하지 않게 하기 위함.
     */
    private static List<String> groupRepr(ArrayNode rows, List<Integer> indices) {
        List<String> reps = new ArrayList<>();
        for (int idx : indices) {
            reps.add(canonicalRow(rows.get(idx)));
        }
        reps.sort(Comparator.naturalOrder());
        return reps;
    }

    private static String canonicalRow(JsonNode row) {
        TreeSet<String> fieldNames = new TreeSet<>();
        row.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.removeAll(VOLATILE_FIELDS);

        StringBuilder sb = new StringBuilder();
        for (String field : fieldNames) {
            String value = asNullableText(row.get(field));
            sb.append(field).append(KV_SEP).append(value == null ? "" : value).append(UNIT_SEP);
        }
        return sb.toString();
    }

    private static boolean fieldDiffers(JsonNode oldRow, JsonNode newRow, String field) {
        return !Objects.equals(asNullableText(oldRow.get(field)), asNullableText(newRow.get(field)));
    }

    private static String asNullableText(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static final Comparator<String> NULLS_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());
    private static final Comparator<NaturalKey> KEY_COMPARATOR = Comparator
            .comparing(NaturalKey::name, NULLS_FIRST)
            .thenComparing(NaturalKey::brewery, NULLS_FIRST)
            .thenComparing(NaturalKey::abv, NULLS_FIRST)
            .thenComparing(NaturalKey::volume, NULLS_FIRST);
}
