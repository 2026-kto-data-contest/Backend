package com.jeontongjuro.backend.brewery;

import java.util.List;
import java.util.Map;

/**
 * 상세 응답의 "한 줄 요약" 카피를 문장 그대로 내려보내는 정책. 대상은 8곳이다.
 * <p>
 * ★출처는 디자이너 리서치(2026-09-19)다. 우리 파이프라인의 어느 원본(brewery_raw·product_raw·nonglim_seed·
 * tour_content·experience_match_seed)에도 없는 값이다. 그래서 DB·시드가 아니라 응답 계층 상수로 둔다 —
 * {@link ContactSupplementPolicy}·{@link UnreachableHomepagePolicy}와 같은 층이다.
 * <p>
 * ★문장을 임의로 다듬지 마라. 맞춤법·띄어쓰기·어미 통일도 금지다. 디자이너 원문 전사이고, 손대는 순간
 * 디자인 시안과 서비스 문구가 어긋난다. 줄 순서도 디자이너 지정이다.
 * <p>
 * ★대상 8곳은 foundedYear·featureTags·experiences 셋 다 비어 있어 프론트가 요약을 조립할 재료가 아예 없는
 * 곳이다. 나머지 50곳은 기존대로 프론트가 그 세 재료로 조립한다 — 서버는 그 50곳 문장을 재현하지 않는다.
 * 비대상은 결측 규약대로 빈 배열이며 null이 아니다.
 * <p>
 * ★BRW-032 양촌양조는 대상이 아니다. 대상인 BRW-033 양촌와이너리(논산 양촌, 감·딸기 와인)와 이름이 비슷하지만
 * 별개 양조장이다. 두 ID를 바꿔 넣지 마라 — {@code BrewerySummaryCopyPolicyTest}가 BRW-032 빈 배열을 지킨다.
 * <p>
 * ★문구가 바뀌면 이 상수를 고치고 배포해야 한다. 런타임에 바꿀 경로는 없다.
 */
public final class BrewerySummaryCopyPolicy {

    /** breweryId → 카피 2줄(디자이너 지정 순서). ★전사만 한다. 문장 가공 금지. */
    private static final Map<String, List<String>> SUMMARY_LINES = Map.of(
            // 금풍양조
            "BRW-007", List.of(
                    "1931년 지어진 옛 양조장에서 3대째 막걸리를 빚어요.",
                    "강화도 친환경 쌀을 사용하고, 감미료를 넣지 않아요."),
            // 다도참주가
            "BRW-008", List.of(
                    "1962년부터 나주에서 3대째 막걸리를 빚어요.",
                    "오래된 양조장의 전통을 현대적인 생산 시설로 이어가요."),
            // 덕유양조
            "BRW-011", List.of(
                    "1990년대부터 무주의 머루로 와인을 만들어왔어요.",
                    "지역 농가의 머루와 직접 재배한 머루를 원료로 사용해요."),
            // 맑은내일
            "BRW-014", List.of(
                    "1945년 창원 사화정미소에서 시작한 양조를 3대째 이어가요.",
                    "지역에서 생산한 쌀을 활용해 창원의 막걸리를 만들어요."),
            // 술아원
            "BRW-029", List.of(
                    "여주산 찹쌀로 전통 방식의 과하주를 빚어요.",
                    "옛 과하주를 복원하고 장기 숙성해 술을 완성해요."),
            // 양촌와이너리
            "BRW-033", List.of(
                    "논산 양촌의 감과 딸기를 활용해 와인을 만들어요.",
                    "지역에서 나는 과일을 와인과 증류주로 다양하게 풀어내요."),
            // 인천탁주
            "BRW-043", List.of(
                    "1974년 인천 지역 11개 양조장이 합쳐져 출범했어요.",
                    "인천을 기반으로 지역 막걸리의 명맥을 이어가요."),
            // 하미앙 와인밸리
            "BRW-055", List.of(
                    "지리산 해발 약 500m에서 산머루를 재배하고 와인을 만들어요.",
                    "와인 투어와 족욕 등 산머루를 활용한 체험을 운영해요."));

    private BrewerySummaryCopyPolicy() {
    }

    /** 대상이면 카피 2줄, 아니면 빈 배열. null·미등록 ID도 빈 배열이다(결측 규약: 배열은 []). */
    public static List<String> summaryLines(String breweryId) {
        return breweryId == null ? List.of() : SUMMARY_LINES.getOrDefault(breweryId, List.of());
    }
}
