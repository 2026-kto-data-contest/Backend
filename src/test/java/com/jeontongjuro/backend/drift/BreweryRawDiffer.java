package com.jeontongjuro.backend.drift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 8/8 brewery raw 스냅샷과 8/7(이전) 스냅샷을 비교해 added/removed/changed/reordered 4버킷으로 분류한다.
 * DB·Spring·brewery/product/override/pipeline 패키지에 의존하지 않는 순수 Jackson 기반 운영 점검 도구.
 */
public final class BreweryRawDiffer {

    /** 자연키. raw 원본 문자열 그대로 비교하며 정규화(norm) 호출 없음. */
    public static final String NAME_FIELD = "상호명";

    /** 개명 힌트 매칭에 쓰는 주소 필드. */
    public static final String ADDRESS_FIELD = "주소";

    /** 조회수처럼 갱신마다 자연 변동하는 필드 — changed 판정에서 제외. */
    public static final Set<String> VOLATILE_FIELDS = Set.of("조회수");

    private BreweryRawDiffer() {
    }

    public record FieldChange(String field, String before, String after) {
    }

    public record ChangedEntry(String name, List<FieldChange> changes) {
    }

    public record ReorderedEntry(String name, int oldIndex, int newIndex) {
    }

    /** removed/added 중 주소가 일치하는 쌍 — 개명 의심 힌트일 뿐, 같은 엔티티로 자동 병합하지 않음. */
    public record RenameHint(String removedName, String addedName, String address) {
    }

    public record DiffResult(
            List<String> added,
            List<String> removed,
            List<ChangedEntry> changed,
            List<ReorderedEntry> reordered,
            List<RenameHint> renameHints,
            int volatileOnlyCount
    ) {
    }

    public static DiffResult diff(JsonNode oldRoot, JsonNode newRoot) {
        ArrayNode oldRows = (ArrayNode) oldRoot.get("data");
        ArrayNode newRows = (ArrayNode) newRoot.get("data");

        Map<String, Integer> oldIndex = indexByName(oldRows);
        Map<String, Integer> newIndex = indexByName(newRows);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<ChangedEntry> changed = new ArrayList<>();
        List<ReorderedEntry> reordered = new ArrayList<>();
        int volatileOnlyCount = 0;

        for (String name : newIndex.keySet()) {
            if (!oldIndex.containsKey(name)) {
                added.add(name);
            }
        }
        for (String name : oldIndex.keySet()) {
            if (!newIndex.containsKey(name)) {
                removed.add(name);
            }
        }

        for (Map.Entry<String, Integer> entry : oldIndex.entrySet()) {
            String name = entry.getKey();
            Integer newIdx = newIndex.get(name);
            if (newIdx == null) {
                continue;
            }
            int oldIdx = entry.getValue();
            JsonNode oldRow = oldRows.get(oldIdx);
            JsonNode newRow = newRows.get(newIdx);

            List<FieldChange> fieldChanges = compareNonVolatileFields(oldRow, newRow);
            boolean volatileDiffers = VOLATILE_FIELDS.stream().anyMatch(field -> fieldDiffers(oldRow, newRow, field));

            if (!fieldChanges.isEmpty()) {
                changed.add(new ChangedEntry(name, fieldChanges));
            } else if (oldIdx != newIdx) {
                reordered.add(new ReorderedEntry(name, oldIdx, newIdx));
            } else if (volatileDiffers) {
                volatileOnlyCount++;
            }
        }

        added.sort(String::compareTo);
        removed.sort(String::compareTo);

        List<RenameHint> renameHints = buildRenameHints(oldRows, newRows, removed, added);

        return new DiffResult(added, removed, changed, reordered, renameHints, volatileOnlyCount);
    }

    private static Map<String, Integer> indexByName(ArrayNode rows) {
        Map<String, List<Integer>> occurrences = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String name = rows.get(i).get(NAME_FIELD).asText();
            occurrences.computeIfAbsent(name, k -> new ArrayList<>()).add(i);
        }

        List<String> duplicates = occurrences.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("동명 업체 감지, 수동 확인 필요: " + duplicates);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        occurrences.forEach((name, indices) -> result.put(name, indices.get(0)));
        return result;
    }

    private static List<FieldChange> compareNonVolatileFields(JsonNode oldRow, JsonNode newRow) {
        Set<String> fieldNames = new LinkedHashSet<>();
        oldRow.fieldNames().forEachRemaining(fieldNames::add);
        newRow.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.remove(NAME_FIELD);
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

    private static boolean fieldDiffers(JsonNode oldRow, JsonNode newRow, String field) {
        return !Objects.equals(asNullableText(oldRow.get(field)), asNullableText(newRow.get(field)));
    }

    private static String asNullableText(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static List<RenameHint> buildRenameHints(
            ArrayNode oldRows, ArrayNode newRows, List<String> removed, List<String> added) {
        Map<String, String> removedAddressByName = new TreeMap<>();
        for (String name : removed) {
            removedAddressByName.put(name, addressOf(oldRows, name));
        }
        Map<String, String> addedAddressByName = new TreeMap<>();
        for (String name : added) {
            addedAddressByName.put(name, addressOf(newRows, name));
        }

        List<RenameHint> hints = new ArrayList<>();
        for (Map.Entry<String, String> removedEntry : removedAddressByName.entrySet()) {
            for (Map.Entry<String, String> addedEntry : addedAddressByName.entrySet()) {
                if (removedEntry.getValue() != null && removedEntry.getValue().equals(addedEntry.getValue())) {
                    hints.add(new RenameHint(removedEntry.getKey(), addedEntry.getKey(), removedEntry.getValue()));
                }
            }
        }
        return hints;
    }

    private static String addressOf(ArrayNode rows, String name) {
        for (JsonNode row : rows) {
            if (row.get(NAME_FIELD).asText().equals(name)) {
                return asNullableText(row.get(ADDRESS_FIELD));
            }
        }
        throw new IllegalStateException("상호명을 찾을 수 없음: " + name);
    }
}
