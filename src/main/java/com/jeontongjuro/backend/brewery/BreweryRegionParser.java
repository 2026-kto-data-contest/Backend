package com.jeontongjuro.backend.brewery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 양조장 주소 → (sido, region) 결정론적 순수 파서. DB·Spring 접근 없음.
 * <p>
 * ★이 파서가 sido·region 값의 유일 정답 출처(SSOT)다. 다음 사이클의 카카오 지오코딩은 좌표(mapX·mapY)만
 * 쓰고, 카카오가 같이 주는 region_1depth 같은 지역 텍스트는 폐기한다 — 지역값을 두 곳에서 만들지 않는다.
 * <p>
 * 파싱 규칙: 주소 문자열 <b>맨 앞 시도 토큰만 앵커</b>로, 등록된 alias 중 <b>최장 프리픽스 일치</b>로 sido를
 * 확정한다. 문자열 중간에 등장하는 지역명(예: "경기도 광주시"의 "광주")에는 절대 걸리지 않는다 —
 * "경기도 광주시"는 시도가 경기(수도권)지 광주(전라)가 아니다.
 * <p>
 * sido 저장 형태 = 17개 광역단체의 정규화된 짧은 형태(서울/부산/…/제주). 원본에 신·구 명칭이 섞여 있어도
 * (강원도/강원특별자치도, 전라북도/전북특별자치도, 세종특별자치시) 저장값은 하나로 통일한다.
 * region = 8칩 롤업값(서비스 지역 필터가 직접 쓰는 값). 부산·울산은 경상에서 독립한 별도 칩이다.
 * <p>
 * 미분류 시도는 <b>조용히 null을 넣지 않고 예외로 시끄럽게 실패</b>한다(실재 검증 가드).
 */
public final class BreweryRegionParser {

    /** 파싱 결과 — 정규 sido 짧은형과 8칩 region. */
    public record RegionResult(String sido, String region) {
    }

    // ── 8칩 region 라벨 (부산·울산 독립) ────────────────────────────────
    private static final String 수도권 = "수도권";
    private static final String 충청 = "충청";
    private static final String 전라 = "전라";
    private static final String 경상 = "경상";
    private static final String 부산칩 = "부산";
    private static final String 울산칩 = "울산";
    private static final String 강원칩 = "강원";
    private static final String 제주칩 = "제주";

    /**
     * sido(정규 짧은형) → region(8칩) 롤업표. ★부산·울산은 경상으로 접지 않는다(독립 칩).
     * 광역권 6분류(부산·울산→경상 폴드백)는 컬럼이 아니라 테스트 교차검증 전용이므로 여기 두지 않는다.
     */
    private static final Map<String, String> SIDO_TO_REGION = Map.ofEntries(
            Map.entry("서울", 수도권), Map.entry("경기", 수도권), Map.entry("인천", 수도권),
            Map.entry("대전", 충청), Map.entry("세종", 충청), Map.entry("충북", 충청), Map.entry("충남", 충청),
            Map.entry("광주", 전라), Map.entry("전북", 전라), Map.entry("전남", 전라),
            Map.entry("대구", 경상), Map.entry("경북", 경상), Map.entry("경남", 경상),
            Map.entry("부산", 부산칩),
            Map.entry("울산", 울산칩),
            Map.entry("강원", 강원칩),
            Map.entry("제주", 제주칩));

    /**
     * 주소 맨 앞 시도 alias → 정규 sido 짧은형. ★삽입 순서 = 매칭 순서 = 최장 프리픽스 우선(길이 내림차순 나열).
     * 신·구 명칭·전체명·짧은형을 모두 등록하되, 한 시도 가족의 모든 alias는 같은 정규형으로 수렴한다.
     * <p>
     * ★함정 방지: bare "충청/전라/경상"은 alias가 아니다(그건 region이지 sido가 아니며, 원본은 항상
     * 충청북도/충청남도/충북/충남 등 구체형으로만 온다). bare 단독 시도(강원·제주·세종·서울 등)만 등록한다.
     */
    private static final Map<String, String> ALIAS_TO_SIDO = buildAliases();

    private static Map<String, String> buildAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        // 8자 — 특별자치 신명칭 (구명칭·짧은형보다 먼저)
        m.put("강원특별자치도", "강원");
        m.put("전북특별자치도", "전북");
        m.put("제주특별자치도", "제주");
        m.put("세종특별자치시", "세종");
        // 4~5자 — 도 전체명 / 광역시 전체명
        m.put("전라북도", "전북");
        m.put("전라남도", "전남");
        m.put("충청북도", "충북");
        m.put("충청남도", "충남");
        m.put("경상북도", "경북");
        m.put("경상남도", "경남");
        m.put("서울특별시", "서울");
        m.put("부산광역시", "부산");
        m.put("대구광역시", "대구");
        m.put("인천광역시", "인천");
        m.put("광주광역시", "광주");
        m.put("대전광역시", "대전");
        m.put("울산광역시", "울산");
        // 3자 — 도 구명칭 / 별명
        m.put("강원도", "강원");
        m.put("경기도", "경기");
        m.put("제주도", "제주");
        m.put("세종시", "세종");
        // 2자 — 짧은 정규형 (단독 시도만; bare 충청/전라/경상 없음)
        m.put("서울", "서울");
        m.put("부산", "부산");
        m.put("대구", "대구");
        m.put("인천", "인천");
        m.put("광주", "광주");
        m.put("대전", "대전");
        m.put("울산", "울산");
        m.put("세종", "세종");
        m.put("경기", "경기");
        m.put("강원", "강원");
        m.put("충북", "충북");
        m.put("충남", "충남");
        m.put("전북", "전북");
        m.put("전남", "전남");
        m.put("경북", "경북");
        m.put("경남", "경남");
        m.put("제주", "제주");
        return m;
    }

    private BreweryRegionParser() {
    }

    /**
     * 주소 → (sido, region). 맨 앞 토큰 최장 프리픽스로 sido 확정 후 8칩 롤업. 미분류·빈주소는 예외.
     *
     * @throws IllegalArgumentException 시도를 분류하지 못한 경우(조용한 null 대신 시끄러운 실패)
     */
    public static RegionResult parse(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소가 비어 있어 시도를 분류할 수 없습니다: [" + address + "]");
        }
        String head = address.strip();
        for (Map.Entry<String, String> e : ALIAS_TO_SIDO.entrySet()) {
            if (head.startsWith(e.getKey())) {
                String sido = e.getValue();
                String region = SIDO_TO_REGION.get(sido);
                if (region == null) {
                    // 롤업표 누락 — alias 표와 롤업표 불일치(개발 실수)를 조용히 넘기지 않는다.
                    throw new IllegalStateException("sido에 대응하는 region 롤업이 없습니다: " + sido);
                }
                return new RegionResult(sido, region);
            }
        }
        throw new IllegalArgumentException("미분류 시도 — alias 표에 없는 앞 토큰입니다: " + address);
    }
}
