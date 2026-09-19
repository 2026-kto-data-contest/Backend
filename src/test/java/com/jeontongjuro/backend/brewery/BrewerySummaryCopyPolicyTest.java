package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

/**
 * 한 줄 요약 카피 정책 단위 테스트.
 * <p>
 * ★문장 전문을 여기에 적는 것이 목적이다. 상수를 고칠 때 오타가 나면 여기서 잡힌다 — 디자이너 원문 전사이므로
 * 맞춤법·띄어쓰기·어미를 "고치는" 수정은 실패해야 한다.
 */
class BrewerySummaryCopyPolicyTest {

    @Nested
    @DisplayName("대상 8곳")
    class Targets {

        @Test
        @DisplayName("BRW-007 금풍양조")
        void brw007() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-007")).containsExactly(
                    "1931년 지어진 옛 양조장에서 3대째 막걸리를 빚어요.",
                    "강화도 친환경 쌀을 사용하고, 감미료를 넣지 않아요.");
        }

        @Test
        @DisplayName("BRW-008 다도참주가")
        void brw008() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-008")).containsExactly(
                    "1962년부터 나주에서 3대째 막걸리를 빚어요.",
                    "오래된 양조장의 전통을 현대적인 생산 시설로 이어가요.");
        }

        @Test
        @DisplayName("BRW-011 덕유양조")
        void brw011() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-011")).containsExactly(
                    "1990년대부터 무주의 머루로 와인을 만들어왔어요.",
                    "지역 농가의 머루와 직접 재배한 머루를 원료로 사용해요.");
        }

        @Test
        @DisplayName("BRW-014 맑은내일")
        void brw014() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-014")).containsExactly(
                    "1945년 창원 사화정미소에서 시작한 양조를 3대째 이어가요.",
                    "지역에서 생산한 쌀을 활용해 창원의 막걸리를 만들어요.");
        }

        @Test
        @DisplayName("BRW-029 술아원")
        void brw029() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-029")).containsExactly(
                    "여주산 찹쌀로 전통 방식의 과하주를 빚어요.",
                    "옛 과하주를 복원하고 장기 숙성해 술을 완성해요.");
        }

        @Test
        @DisplayName("BRW-033 양촌와이너리 — BRW-032 양촌양조가 아니다")
        void brw033() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-033")).containsExactly(
                    "논산 양촌의 감과 딸기를 활용해 와인을 만들어요.",
                    "지역에서 나는 과일을 와인과 증류주로 다양하게 풀어내요.");
        }

        @Test
        @DisplayName("BRW-043 인천탁주")
        void brw043() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-043")).containsExactly(
                    "1974년 인천 지역 11개 양조장이 합쳐져 출범했어요.",
                    "인천을 기반으로 지역 막걸리의 명맥을 이어가요.");
        }

        @Test
        @DisplayName("BRW-055 하미앙 와인밸리")
        void brw055() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-055")).containsExactly(
                    "지리산 해발 약 500m에서 산머루를 재배하고 와인을 만들어요.",
                    "와인 투어와 족욕 등 산머루를 활용한 체험을 운영해요.");
        }

        @Test
        @DisplayName("8곳 전부 정확히 2줄이며 디자이너 지정 순서가 보존된다")
        void everyTargetHasTwoLinesInOrder() {
            for (String id : java.util.List.of(
                    "BRW-007", "BRW-008", "BRW-011", "BRW-014", "BRW-029", "BRW-033", "BRW-043", "BRW-055")) {
                assertThat(BrewerySummaryCopyPolicy.summaryLines(id)).as(id).hasSize(2);
            }
            // 순서 보존: 첫 줄이 연혁, 둘째 줄이 특징이다. 뒤집히면 여기서 잡힌다.
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-029").get(0))
                    .isEqualTo("여주산 찹쌀로 전통 방식의 과하주를 빚어요.");
        }
    }

    @Nested
    @DisplayName("비대상")
    class NonTargets {

        @Test
        @DisplayName("★BRW-032 양촌양조는 대상이 아니다 — BRW-033 양촌와이너리와 혼동 방어")
        void brw032IsNotATarget() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-032")).isEmpty();
        }

        @Test
        @DisplayName("대상 밖 양조장은 빈 배열")
        void otherBreweriesAreEmpty() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-058")).isEmpty();
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-001")).isEmpty();
        }

        @Test
        @DisplayName("null·미등록 ID도 빈 배열(NPE 아님)")
        void nullAndUnknownAreEmpty() {
            assertThat(BrewerySummaryCopyPolicy.summaryLines(null)).isEmpty();
            assertThat(BrewerySummaryCopyPolicy.summaryLines("BRW-999")).isEmpty();
            assertThat(BrewerySummaryCopyPolicy.summaryLines("")).isEmpty();
        }
    }
}
