package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수상 등급 파서 단위 검증(DB 없음). 스펙 §⑥의 케이스를 크래프트 입력으로 고정한다.
 * 실데이터 스냅샷 회귀는 {@link ProductQueryGoldenTest}가 별도로 잡는다.
 */
class AwardGradeParserTest {

    @Test
    @DisplayName("대회명 '한국와인대상' 제거 → 실버상은 은상 계열(대상 아님)")
    void stripsCompetitionNameHankukWineDaesang() {
        assertThat(AwardGradeParser.parse("2014년 한국와인대상 실버상")).isEqualTo("은상");
    }

    @Test
    @DisplayName("대회명 '주류대상' 뒤에 실제 대상 → 대상")
    void juryuDaesangCompetitionThenRealDaesang() {
        assertThat(AwardGradeParser.parse("2021 대한민국 주류대상우리술 탁주 생막걸리 부문 3년 연속 대상"))
                .isEqualTo("대상");
    }

    @Test
    @DisplayName("제품명 '골드와인' 제거 → 실제 금상만 매칭")
    void stripsProductNameGoldWine() {
        assertThat(AwardGradeParser.parse("골드와인 금상")).isEqualTo("금상");
    }

    @Test
    @DisplayName("괄호 안 쉼표에 안 깨짐 → 장려상")
    void notBrokenByCommaInsideParens() {
        assertThat(AwardGradeParser.parse("2013 대한민국우리술품평회 장려상 (38%, 일반증류주 부문)"))
                .isEqualTo("장려상");
    }

    @Test
    @DisplayName("연도 나열 쉼표에 안 깨짐 → 최우수상")
    void notBrokenByYearListComma() {
        assertThat(AwardGradeParser.parse("2019 최우수상, 2015, 2016, 2018 아시아 와인트로피 금상 수상"))
                .isEqualTo("최우수상");
    }

    @Test
    @DisplayName("무구분자 '우수상2017' → 우수상")
    void matchesWithoutDelimiter() {
        assertThat(AwardGradeParser.parse("2011 우리술 품평회 우수상2017 우리술 품평회 우수상"))
                .isEqualTo("우수상");
    }

    @Test
    @DisplayName("만찬주/기념주/건배주만 있음 → 등급 아님 → fallback '수상'")
    void ceremonialOnlyFallsBack() {
        assertThat(AwardGradeParser.parse("2004 청와대 국가안전보장회의 기념주, 2005 한미우호의 밤 만찬주, 건배주"))
                .isEqualTo("수상");
    }

    @Test
    @DisplayName("절단 awards에 등급 있음 → 그 등급 채택(브론즈상 → 동상)")
    void truncatedWithGradeTakesGrade() {
        assertThat(AwardGradeParser.parse(
                "International Wine Challenge 심사위원추천상 수상, London Wine Competition 브론즈상 수상, 대한민국주"))
                .isEqualTo("동상");
    }

    @Test
    @DisplayName("절단 awards에 인식 등급 없음 → '수상'")
    void truncatedWithoutGradeFallsBack() {
        assertThat(AwardGradeParser.parse("International Wine Challenge 심사위원추천상 수상, 대한민국주"))
                .isEqualTo("수상");
    }

    @Test
    @DisplayName("음역 등급: 골드→금상, 실버→은상, 브론즈→동상")
    void transliteratedGrades() {
        assertThat(AwardGradeParser.parse("프랑스 Paris Wine Cup 2021 실버 메달 수상")).isEqualTo("은상");
        assertThat(AwardGradeParser.parse("파리 와인컵 2021 브론즈 메달")).isEqualTo("동상");
    }

    @Test
    @DisplayName("최우수상 안의 '우수상' 겹침 → 최우수상이 이긴다")
    void choeUsujangNotConfusedWithUsujang() {
        assertThat(AwardGradeParser.parse("2020 대한민국 우리술품평회 탁주 최우수상")).isEqualTo("최우수상");
    }

    @Test
    @DisplayName("대통령표창은 대통령상이 아니다 → 같은 문자열의 더 높은 대상이 이긴다")
    void presidentCitationNotPresidentAward() {
        assertThat(AwardGradeParser.parse("2001 전통주 대상 수상, 2007 대통령표창")).isEqualTo("대상");
    }

    @Test
    @DisplayName("대통령상은 등급 사전에 없음(양조장 단위 자료, 제품 카드 미부착) → 다른 등급어 없으면 fallback '수상'")
    void presidentAwardIsNotAProductGrade() {
        assertThat(AwardGradeParser.parse("2020 우리술 품평회 증류주 대통령상")).isEqualTo("수상");
    }

    @Test
    @DisplayName("대통령상과 다른 등급어가 함께 있으면 그 다른 등급이 잡힌다(대통령상은 등급 사전에서 무시됨)")
    void presidentAwardIgnoredWhenOtherGradePresent() {
        assertThat(AwardGradeParser.parse("2020 우리술 품평회 증류주 대통령상, 지역 발전 유공 표창"))
                .isEqualTo("표창");
    }

    @Test
    @DisplayName("선정만 남으면 승자로 반환 → '선정' (실데이터 sr58: 대회명 '대한민국주류대상' 제거 후 선정만 남음)")
    void seonjeongWins() {
        // 실데이터(product_raw.source_row_index=58). NAME_NOISE '대한민국주류대상' 제거 후 남는 등급어는 '선정'뿐 →
        // 표창·선정이 '승자'로 반환되는 경로를 봉인한다(기존 테스트는 전부 상위 등급에 밀린 '패자' 케이스였다).
        assertThat(AwardGradeParser.parse("2019대한민국주류대상, 2017년 공식건배주선정")).isEqualTo("선정");
    }

    @Test
    @DisplayName("표창만 있으면 승자로 반환 → '표창' (합성 입력 — 표창이 단독 승자인 실데이터는 없음)")
    void pyochangWins() {
        // ★합성 케이스: 실데이터에서 '표창'은 항상 상위 등급(sr276은 '대상')과 공존해 단독 승자가 된 적이 없다.
        //   표창 라벨 매핑이 깨져도 아무 테스트가 못 잡는 사각을 합성 입력으로 봉인한다.
        assertThat(AwardGradeParser.parse("2020 지역 농업 발전 유공 표창")).isEqualTo("표창");
    }

    @Test
    @DisplayName("awards null → null")
    void nullAwards() {
        assertThat(AwardGradeParser.parse(null)).isNull();
        assertThat(AwardGradeParser.parse("   ")).isNull();
    }
}
