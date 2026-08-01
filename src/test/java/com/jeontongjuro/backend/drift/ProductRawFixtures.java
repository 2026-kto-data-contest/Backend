package com.jeontongjuro.backend.drift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 20260728 product 골든을 코드로 변형해 "가짜 8/8" 스냅샷을 만드는 합성 fixture 생성기.
 * 골든 원본은 절대 수정하지 않고 항상 deepCopy 위에서 변형한다.
 *
 * <p>brewery fixtures와 달리 상호명(제품명)이 유일하지 않으므로 변형 대상은 이름이 아니라
 * 배열 인덱스로 지정한다 — 그룹·동명 케이스에서도 어느 행을 건드리는지 모호하지 않게 하기 위함.
 */
public final class ProductRawFixtures {

    private ProductRawFixtures() {
    }

    /** 지정 인덱스 행의 특정 필드를 문자열 값으로 바꾼다(null → 값 케이스 포함). */
    public static JsonNode flipAt(JsonNode root, int index, String field, String newValue) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        ((ObjectNode) rows.get(index)).put(field, newValue);
        return copy;
    }

    /** 맨 끝에 신규 1행을 추가한다(배열 인덱스 밀림 없음). */
    public static JsonNode add(JsonNode root, ObjectNode newRow) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        rows.add(newRow.deepCopy());
        return copy;
    }

    /** 지정 인덱스 1행을 삭제한다(아래 행들의 배열 인덱스가 앞으로 밀림). */
    public static JsonNode removeAt(JsonNode root, int index) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ArrayNode rows = (ArrayNode) copy.get("data");
        rows.remove(index);
        return copy;
    }

    /** 지정 인덱스 행의 깊은 복사본을 돌려준다 — 신규 행(동일 키 그룹 추가 등)을 만들 재료로 쓴다. */
    public static ObjectNode copyRow(JsonNode root, int index) {
        ArrayNode rows = (ArrayNode) root.get("data");
        return (ObjectNode) rows.get(index).deepCopy();
    }

    /**
     * 값은 그대로, 배열 순서만 섞는다. 좌측 1칸 회전(rotate-left-by-1)을 사용한다 —
     * n&gt;1이면 모든 원소의 인덱스가 반드시 바뀌는(고정점 없는) 결정적 치환이라
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
