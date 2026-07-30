package com.jeontongjuro.backend.drift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 20260728 골든을 코드로 변형해 "가짜 8/8" 스냅샷을 만드는 합성 fixture 생성기.
 * 골든 원본은 절대 수정하지 않고 항상 deepCopy 위에서 변형한다.
 */
public final class BreweryRawFixtures {

    private BreweryRawFixtures() {
    }

    /** 특정 상호명의 특정 필드 값을 바꾼다(null → 값 케이스에도 재사용 가능). */
    public static JsonNode flip(JsonNode root, String name, String field, String newValue) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        for (JsonNode row : rows) {
            if (row.get(BreweryRawDiffer.NAME_FIELD).asText().equals(name)) {
                ((ObjectNode) row).put(field, newValue);
                return copy;
            }
        }
        throw new IllegalArgumentException("상호명을 찾을 수 없음: " + name);
    }

    /** 맨 끝에 신규 상호명 1행을 추가한다. */
    public static JsonNode add(JsonNode root, ObjectNode newRow) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        rows.add(newRow.deepCopy());
        return copy;
    }

    /** 지정한 상호명 1행을 삭제한다(아래 행들의 배열 인덱스가 앞으로 밀림). */
    public static JsonNode remove(JsonNode root, String name) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).get(BreweryRawDiffer.NAME_FIELD).asText().equals(name)) {
                rows.remove(i);
                return copy;
            }
        }
        throw new IllegalArgumentException("상호명을 찾을 수 없음: " + name);
    }

    /**
     * 값은 그대로, 배열 순서만 섞는다. 좌측 1칸 회전(rotate-left-by-1)을 사용한다 —
     * n>1이면 모든 원소의 인덱스가 반드시 바뀌는(고정점 없는) 결정적 치환이라
     * "행 개수 홀수일 때 가운데 원소만 제자리(reverse의 함정)" 같은 우연을 피한다.
     */
    public static JsonNode shuffle(JsonNode root) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        if (rows.size() > 1) {
            JsonNode first = rows.remove(0);
            rows.add(first);
        }
        return copy;
    }
}
