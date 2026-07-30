package com.jeontongjuro.backend.pipeline.normalize;

import static com.jeontongjuro.backend.pipeline.normalize.BusinessNameNormalizer.normalize;
import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T2-a 정규화 규칙 단위 테스트. 정규화 결과의 "정답 골든"은 없으므로(§작업3 사유) 규칙 원문
 * ({@code ~/docs/audit/20260728_dump/파생규칙_원문.md} §T2-a)과 파트 A 결정
 * ({@code docs/audit/20260730_join_decision_ledger.md})에 대한 회귀 감지형 테스트다.
 */
class BusinessNameNormalizerTest {

    @Test
    @DisplayName("null 입력은 null 반환")
    void nullIsNull() {
        assertThat(normalize(null)).isNull();
    }

    @Test
    @DisplayName("공백 제거")
    void whitespaceRemoved() {
        assertThat(normalize("조은술 세종")).isEqualTo("조은술세종");
    }

    @Test
    @DisplayName("법인격 제거 — 위치 무관(접두·접미·중간)")
    void corporateDesignatorsRemovedRegardlessOfPosition() {
        assertThat(normalize("농업회사법인(주) 두레박")).isEqualTo("두레박");
        assertThat(normalize("주식회사 국순당")).isEqualTo("국순당");
        // ㈜ 제거(법인격) 후 '배상면주가' → 말미 접미어 '주가' 제거(4단계는 3단계 이후 적용) → '배상면'
        assertThat(normalize("배상면주가㈜")).isEqualTo("배상면");
    }

    @Test
    @DisplayName("말미 접미어 최장일치 1회 제거 — 원문 목록")
    void trailingSuffixLongestMatchOriginal() {
        assertThat(normalize("산머루농원")).isEqualTo("산머루");
        assertThat(normalize("배혜정도가")).isEqualTo("배혜정");
        assertThat(normalize("그린영농조합")).isEqualTo("그린");
    }

    @Test
    @DisplayName("파트 A 신규 접미어: 주조장·양조원 편입")
    void newSuffixesFromPartA() {
        assertThat(normalize("해창주조장")).isEqualTo("해창");
        assertThat(normalize("문배주양조원")).isEqualTo("문배주");
    }

    @Test
    @DisplayName("NFC 전처리: NFD 인코딩 원본도 NFC 정규화 후 동일 norm")
    void nfcPreprocessingHandlesNfdInput() {
        String nfd = Normalizer.normalize("풍정사계", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("풍정사계"); // 사전조건: 실제로 다른 코드포인트 시퀀스인지 확인
        assertThat(normalize(nfd)).isEqualTo(normalize("풍정사계"));
    }

    @Test
    @DisplayName("★핵심 — 태인합동주조장 vs 태인양조장은 규칙수정 후에도 서로 다른 norm(합동 미해소, override 대상)")
    void taeinCaseRemainsUnresolvedByRuleAlone() {
        assertThat(normalize("태인합동주조장")).isEqualTo("태인합동");
        assertThat(normalize("태인양조장")).isEqualTo("태인");
        assertThat(normalize("태인합동주조장")).isNotEqualTo(normalize("태인양조장"));
    }

    @Test
    @DisplayName("금지 준수 — 목록 밖 토큰(합동/와이너리/브루어리)은 접미어로 취급되지 않는다(오병합 방지)")
    void forbiddenTokensAreNotTreatedAsSuffixes() {
        assertThat(normalize("아무개와이너리")).isEqualTo("아무개와이너리");
        assertThat(normalize("아무개브루어리")).isEqualTo("아무개브루어리");
        assertThat(normalize("아무개합동")).isEqualTo("아무개합동");
    }

    @Test
    @DisplayName("농업회사(법인 없이)는 법인격 제거 대상이 아니며 접미어도 아니므로 그대로 잔류 — 갈기산농업회사는 override 대상 유지")
    void bareAgriculturalCorpWithoutLegalSuffixIsNotStripped() {
        assertThat(normalize("갈기산농업회사")).isEqualTo("갈기산농업회사");
        assertThat(normalize("갈기산농업회사")).isNotEqualTo(normalize("갈기산"));
    }
}
