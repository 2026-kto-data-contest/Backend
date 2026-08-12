package com.jeontongjuro.backend.experience;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * odcloud 15089109 응답 1행의 파싱 결과(저장 대상 필드만). 원본 12필드 중 매칭키 1(양조장명)과 저장 5
 * (프로그램명·내용·장소·소요시간·비용)만 담는다 — 주소·연락처·홈페이지·주종·상시/예약방문은 읽지 않는다
 * (2021 값 마스터 오염 차단, 애초에 추출하지 않아 구조로 봉쇄).
 * <p>
 * 정규화: 문자열 필드의 빈 문자열/공백-only는 null로 정규화(first_image 빈 문자열 1,026건 선례). cost는
 * 0(무료)과 null(미입력)을 엄격 구분 — 숫자면 그 값(0 포함), 결측/비숫자면 null.
 */
public record ExperienceRow(
        String breweryName,
        String programName,
        String content,
        String place,
        String duration,
        Integer cost
) {

    private static final String K_BREWERY = "양조장명";
    private static final String K_PROGRAM = "체험프로그램명";
    private static final String K_CONTENT = "내용";
    private static final String K_PLACE = "장소";
    private static final String K_DURATION = "소요시간";
    private static final String K_COST = "투어비용(원)";

    /** API JSON 노드 1개 → 저장 대상 필드 파싱. 빈 문자열→null, cost는 숫자만(0 보존). */
    public static ExperienceRow fromJson(JsonNode node) {
        return new ExperienceRow(
                text(node, K_BREWERY),
                text(node, K_PROGRAM),
                text(node, K_CONTENT),
                text(node, K_PLACE),
                text(node, K_DURATION),
                intOrNull(node, K_COST));
    }

    /** 문자열 필드 — 결측/JSON null/공백-only는 null, 그 외 trim 없이 원문 보존(내부 공백·개행 유지). */
    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    /** 정수 필드 — 숫자면 그 값(0 포함), 결측/JSON null/비숫자는 null(0과 null을 절대 같게 다루지 않는다). */
    private static Integer intOrNull(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.asInt();
    }
}
