package com.jeontongjuro.backend.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 추천 양조장의 운영 지정 고정 순서 시드 로더. {@code src/main/resources/recommended_brewery_seed.json}을
 * 기동 시 한 번 읽어 인메모리 {@code List<String>}(brewery_id, 노출 순서)으로 노출한다.
 * <p>
 * ★DB 테이블·컬럼을 신설하지 않는다 — {@link RecommendedBreweryService}가 비로그인·온보딩 전 노출과
 * 취향 정렬 부족분 채우기에 이 순서를 쓴다. {@code product/query/ProductExclusionSeed}와 같은 JSON 파싱
 * 패턴을 따른다(파이프라인 시드가 아니라 애플리케이션 기동 시 읽는 방식).
 * <p>
 * entries가 비어 있어도 정상이다(운영이 아직 채우지 않은 초기 상태) — 이 경우 호출자는 상호명 가나다순만으로
 * 노출한다.
 */
@Component
public class FixedBrewerySeed {

    private static final String SEED_CLASSPATH = "/recommended_brewery_seed.json";

    /** 기동 시 1회 로드해 고정(리소스는 배포 산출물이라 런타임 변경 없음). */
    private final List<String> orderedBreweryIds;

    public FixedBrewerySeed(ObjectMapper objectMapper) {
        this.orderedBreweryIds = load(objectMapper);
    }

    /** 운영이 지정한 노출 순서(brewery_id 목록). 시드가 비어 있으면 빈 리스트. */
    public List<String> orderedBreweryIds() {
        return orderedBreweryIds;
    }

    private static List<String> load(ObjectMapper objectMapper) {
        try (InputStream in = FixedBrewerySeed.class.getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("추천 양조장 고정 목록 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException(
                        "추천 양조장 고정 목록 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode entry : entries) {
                JsonNode breweryId = entry.get("brewery_id");
                if (breweryId == null || !breweryId.isTextual()) {
                    throw new IllegalStateException(
                            "추천 양조장 고정 목록 시드 항목에 문자열 brewery_id 없음 — " + SEED_CLASSPATH);
                }
                ids.add(breweryId.asText());
            }
            return List.copyOf(ids);
        } catch (IOException ex) {
            throw new IllegalStateException("추천 양조장 고정 목록 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
