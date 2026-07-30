package com.jeontongjuro.backend.pipeline.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.pipeline.normalize.BusinessNameNormalizer;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * verify 3종(회귀 감지형): T2-a 정규화 첫 구현(파트 A 반영: 주조장·양조원·NFC)이
 * 골든 픽스처 대비 기존 56/59 JOINED를 깨지 않는지 확인한다.
 * 정규화 결과 자체의 "정답 골든"은 없다(작업3 사유) — 대신 이 테스트가 향후 회귀 기준선이다.
 * 근거 문서: docs/audit/20260730_join_decision_ledger.md, docs/audit/20260730_coverage_diagnosis.md,
 * docs/audit/20260730_rule_remeasure.md(본 재측정 결과 기록).
 * DB·Spring·라이브 API 불필요 — 골든 픽스처를 in-memory로 직접 읽어 재측정한다(골든 파일 무수정).
 */
class NormalizeGoldenRegressionTest {

    private static final String GOLDEN_DIR = "/golden/";
    private static final String BREWERY_FILE = GOLDEN_DIR + "20260728_brewery_raw.json";
    private static final String PRODUCT_FILE = GOLDEN_DIR + "20260728_product_raw.json";

    /** 회귀 기준선 — 규칙수정(파트 A: 주조장·양조원·NFC) 전 unique product norm 개수. T2-b "448"과 일치. */
    private static final int PRODUCT_NORM_COUNT_BEFORE_PART_A = 448;
    /** 회귀 기준선 — 규칙수정 후 unique product norm 개수(병영/사곡/풍정사계 3건 병합으로 -3). */
    private static final int PRODUCT_NORM_COUNT_AFTER_PART_A = 445;

    private static final Set<String> EXPECTED_UNJOINED = Set.of(
            "양촌와이너리", "밀과노닐다", "국가유산·명인 조옥화 안동소주");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("2-1: unique product norm 개수 — 규칙수정 전 448 → 후 445 (병영·사곡·풍정사계 3건 병합)")
    void productNormCountChangesByExactlyThreeMerges() throws IOException {
        assertThat(uniqueProductNormCount(BusinessNameNormalizer::normalize))
                .isEqualTo(PRODUCT_NORM_COUNT_AFTER_PART_A);
        // 규칙수정 전(구규칙: 주조장/양조원/NFC 없이)과 대조 — 델타 3건 확인용 기준선
        assertThat(uniqueProductNormCount(NormalizeGoldenRegressionTest::normalizeWithOldRule))
                .isEqualTo(PRODUCT_NORM_COUNT_BEFORE_PART_A);
    }

    @Test
    @DisplayName("2-2: 커버리지(JOINED brewery 수) 56/59 — 규칙수정 후에도 회귀 없이 그대로")
    void coverageRemains56Of59AfterRuleChange() throws IOException {
        Map<String, Integer> joinedProductCountByBreweryName = joinedProductCounts();
        long joinedCount = joinedProductCountByBreweryName.values().stream().filter(c -> c > 0).count();

        assertThat(joinedProductCountByBreweryName).hasSize(59);
        assertThat(joinedCount).as("커버리지(JOINED 수) — 규칙수정만으로는 변하지 않는다").isEqualTo(56);

        Set<String> actualUnjoined = new HashSet<>();
        joinedProductCountByBreweryName.forEach((name, count) -> {
            if (count == 0) {
                actualUnjoined.add(name);
            }
        });
        assertThat(actualUnjoined).as("UNJOINED 3건 — 회귀 없음").isEqualTo(EXPECTED_UNJOINED);
    }

    @Test
    @DisplayName("2-2 구분: 제품수 회수(놓친제품 회수)는 커버리지 변화와 별개 — 풍정사계만 3→4 회수, 태인/해창은 제품수 불변")
    void productCountRecoveryIsDistinctFromCoverageChange() throws IOException {
        Map<String, Integer> counts = joinedProductCounts();
        // 커버리지 변화(UNJOINED→JOINED)는 없음 — 위 테스트에서 확인됨
        // 이미 JOINED된 brewery 내부의 제품수 회수만 발생: 풍정사계 NFC 정규화로 1건 추가 회수(3→4)
        assertThat(counts.get("풍정사계")).isEqualTo(4);
        // 태인합동주조장·해창주조장은 norm 문자열만 바뀌고(태인합동주조장→태인합동, 해창주조장→해창) 자기 제품수는 불변(1→1)
        assertThat(counts.get("태인합동주조장")).isEqualTo(1);
        assertThat(counts.get("해창주조장")).isEqualTo(1);
    }

    @Test
    @DisplayName("2-3: 태인 케이스 — 주조장 편입 후에도 미해소(태인합동 ≠ 태인) → override로 강등, 규칙 추가 확대 안 함")
    void taeinCaseStaysUnresolvedAndIsNotAbsorbedByRule() {
        assertThat(BusinessNameNormalizer.normalize("태인합동주조장")).isEqualTo("태인합동");
        assertThat(BusinessNameNormalizer.normalize("태인양조장")).isEqualTo("태인");
        assertThat(BusinessNameNormalizer.normalize("태인합동주조장"))
                .as("합동을 접미어로 추가하지 않았으므로 서로 다른 norm 유지")
                .isNotEqualTo(BusinessNameNormalizer.normalize("태인양조장"));
    }

    @Test
    @DisplayName("2-3: 풍정사계 하(夏) — NFC 전처리 후 4건 모두 조인(3→4)")
    void pungjeongsaogyeAllFourJoinAfterNfc() throws IOException {
        assertThat(joinedProductCounts().get("풍정사계")).isEqualTo(4);
    }

    @Test
    @DisplayName("2-4: 파트 B override 8행 — 규칙수정 후에도 전부 norm 불일치로 미조인 유지(잠정→확정, 흡수 0건)")
    void partBOverrideRowsAllRemainUnresolvedByRuleChange() {
        // {product raw명, 연결 대상 brewery 상호명}
        String[][] partB = {
                {"양촌감 와이너리", "양촌와이너리"},
                {"양촌감영농조합법인", "양촌와이너리"},
                {"민속주안동소주", "국가유산·명인 조옥화 안동소주"},
                {"제이엘", "오미나라"},
                {"솔송주(명가원)", "솔송주"},
                {"갈기산농업회사", "갈기산"},
        };
        for (String[] row : partB) {
            String productNorm = BusinessNameNormalizer.normalize(row[0]);
            String breweryNorm = BusinessNameNormalizer.normalize(row[1]);
            assertThat(productNorm)
                    .as("%s(norm=%s) vs %s(norm=%s) — 규칙수정으로 흡수되지 않음, override 유지",
                            row[0], productNorm, row[1], breweryNorm)
                    .isNotEqualTo(breweryNorm);
        }
        // 조옥화 안동소주 25도/45도 2행은 원본 '양조장' 필드가 null이라 애초에 정규화 비교 대상이 아님(MANUAL_DOMAIN, recheck_flag=Y)
        assertThat(BusinessNameNormalizer.normalize((String) null)).isNull();
    }

    /** brewery 상호명(59) → 새 규칙 적용 시 매칭되는 product 개수(0이면 UNJOINED). */
    private Map<String, Integer> joinedProductCounts() throws IOException {
        Map<String, Integer> productNormCounts = normProductCounts(BusinessNameNormalizer::normalize);
        Map<String, Integer> result = new HashMap<>();
        for (JsonNode brewery : readData(BREWERY_FILE)) {
            String businessName = brewery.get("상호명").asText();
            String norm = BusinessNameNormalizer.normalize(businessName);
            result.put(businessName, productNormCounts.getOrDefault(norm, 0));
        }
        return result;
    }

    private int uniqueProductNormCount(java.util.function.UnaryOperator<String> normalizer) throws IOException {
        return normProductCounts(normalizer).size();
    }

    private Map<String, Integer> normProductCounts(java.util.function.UnaryOperator<String> normalizer)
            throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode product : readData(PRODUCT_FILE)) {
            JsonNode breweryNameNode = product.get("양조장");
            if (breweryNameNode == null || breweryNameNode.isNull()) {
                continue; // 원본 '양조장' 빈값 61건 — 정규화 비교 대상 아님(T2-b)
            }
            String norm = normalizer.apply(breweryNameNode.asText());
            counts.merge(norm, 1, Integer::sum);
        }
        return counts;
    }

    /** 구규칙(파트 A 이전): NFC 전처리 없음, 접미어 목록에 주조장·양조원 없음. 회귀 델타 대조 전용. */
    private static String normalizeWithOldRule(String rawName) {
        if (rawName == null) {
            return null;
        }
        String value = rawName.replaceAll("\\s+", "");
        for (String designator : new String[]{
                "농업회사법인", "영농조합법인", "주식회사", "유한회사", "(주)", "(유)", "㈜"}) {
            value = value.replace(designator, "");
        }
        for (String suffix : new String[]{"영농조합", "양조장", "양조", "주조", "도가", "명가", "농원", "농장", "주가"}) {
            if (value.endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        return value;
    }

    private Iterable<JsonNode> readData(String classpathLocation) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(classpathLocation)) {
            assertThat(in).as("클래스패스 리소스 존재: %s", classpathLocation).isNotNull();
            return objectMapper.readTree(in).get("data");
        }
    }
}
