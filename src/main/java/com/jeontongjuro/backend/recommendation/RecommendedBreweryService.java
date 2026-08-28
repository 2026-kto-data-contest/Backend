package com.jeontongjuro.backend.recommendation;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.brewery.query.BrewerySearchCondition;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.onboarding.OnboardingPreference;
import com.jeontongjuro.backend.onboarding.OnboardingPreferenceRepository;
import com.jeontongjuro.backend.onboarding.PreferenceCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 양조장 조회(GET /api/v1/recommendations/breweries) — 홈 「추천 양조장」 섹션, 검색
 * 「이런 양조장은 어때요?」 그리드, 두 화면의 '더보기' 전체 목록이 공유하는 단일 진입점이다.
 * <p>
 * <b>필터가 아니라 정렬이다.</b> 매칭 우선순위 "주종 &gt; 지역 &gt; 맛"을 점수로 환산해 전체 양조장을
 * 재정렬한다 — 점수 0인 양조장도 결과에 포함되고 뒤 순위로 밀릴 뿐이다.
 * <pre>
 * ① 비로그인                    → 고정 목록 순서
 * ② 로그인 + 온보딩 전(취향 없음)  → 고정 목록 순서 (①과 결과 동일)
 * ③ 로그인 + 온보딩 후(취향 있음)  → 취향 점수 내림차순(동점 시 상호명 가나다순)
 * </pre>
 * ★'맛' 축은 별도 구현이 필요 없다 — 온보딩 1단계는 "어떤 맛의 술을 좋아하세요?"로 묻지만, 선택값은
 * 화면에서 이미 주종으로 매핑돼 {@link PreferenceCategory#LIQUOR_TYPE}로 저장된다. 즉 명세의
 * "주종 &gt; 지역 &gt; 맛"에서 맛은 주종의 사용자 표현일 뿐 별도 매칭 입력이 아니고, 현재 점수식
 * (주종 {@value #LIQUOR_MATCH_SCORE}점 + 지역 {@value #REGION_MATCH_SCORE}점)이 세 축을 전부 충족한다.
 * <p>
 * 카드 매핑은 새로 만들지 않는다 — {@link BreweryQueryService#search}가 이미 하는 태그·도수·주종·이미지·
 * 시군구·맛태그·소개 배치 조회 결과({@link BreweryListItemResponse})를 그대로 재사용해 순서만 바꾼다.
 * 모집단이 전 양조장(≤{@value #POPULATION_FETCH_SIZE})이라 한 번에 전량을 읽어 인메모리로 재정렬한다
 * ({@link BreweryQueryService#searchByAccuracy}와 동일한 전략 — DB 페이징 이점이 없는 작은 모집단).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendedBreweryService {

    /** 페이지 기본 크기(size 미지정·비정상 시) — 홈 섹션 6개 기준. */
    static final int DEFAULT_SIZE = 6;
    /** 페이지 최대 크기. 목록 API(BreweryQueryService)와 동일 상한. */
    static final int MAX_SIZE = 100;
    /** 전체 모집단을 한 번에 읽기 위한 조회 크기(현재 양조장 59곳 &lt; 100 이라 한 페이지로 전량 커버). */
    private static final int POPULATION_FETCH_SIZE = MAX_SIZE;
    /** 주종 일치 가중치. 지역보다 커야 "주종 &gt; 지역" 서열이 정렬에 반영된다. */
    private static final int LIQUOR_MATCH_SCORE = 2;
    /** 지역 일치 가중치. */
    private static final int REGION_MATCH_SCORE = 1;

    private final BreweryQueryService breweryQueryService;
    private final MemberRepository memberRepository;
    private final OnboardingPreferenceRepository onboardingPreferenceRepository;
    private final FixedBrewerySeed fixedBrewerySeed;

    public PageResponse<BreweryListItemResponse> recommend(Long memberId, int page, int size) {
        int clampedPage = clampPage(page);
        int clampedSize = clampSize(size);

        List<BreweryListItemResponse> allBreweries = allBreweriesAlphabetical();
        List<BreweryListItemResponse> ordered = orderFor(memberId, allBreweries, clampedSize);

        return slice(ordered, clampedPage, clampedSize);
    }

    /** 전체 양조장을 목록 API와 동일한 카드로, 동일한 고정 정렬(상호명 ASC → breweryId ASC)로 읽는다. */
    private List<BreweryListItemResponse> allBreweriesAlphabetical() {
        BrewerySearchCondition noFilter = BrewerySearchCondition.of(
                null, null, null, null, null, null, null);
        return breweryQueryService.search(noFilter, 0, POPULATION_FETCH_SIZE).content();
    }

    /**
     * ① 비로그인 / ② 온보딩 전은 고정 목록, ③ 온보딩 후는 취향 정렬. ①·②는 같은 분기를 탄다
     * (별도 분기를 두지 않는다 — 결과가 동일하므로).
     */
    private List<BreweryListItemResponse> orderFor(Long memberId, List<BreweryListItemResponse> all,
                                                    int requestedSize) {
        Member member = memberId == null ? null : memberRepository.findById(memberId).orElse(null);
        if (member != null && member.isOnboardingCompleted()) {
            return tasteRanked(member.getId(), all, requestedSize);
        }
        return fixedOrder(all);
    }

    /**
     * 취향 점수 내림차순 정렬 — 동점은 상호명 가나다순, 그마저 같으면 breweryId 오름차순으로 명시적으로
     * 확정한다({@link BreweryQueryService}의 FIXED_SORT와 같은 서열). 점수는 항목당 한 번만 계산해 정렬
     * 비교마다 재계산하지 않는다(비교 횟수는 O(n log n), 점수 계산은 O(n)).
     * <p>
     * 부족분 채우기: 정렬 결과가 이미 전체 모집단이라 요청 size가 모집단 이하면 항상 충분하다 — 모집단
     * ({@value #POPULATION_FETCH_SIZE}곳 이하)보다 큰 size를 요청받는 경우에만 아래 채우기가 실행되고,
     * 그 경우에도 고정 목록이 같은 모집단에서 뽑히므로 중복 제거 후 실제로 추가되는 항목은 없다.
     */
    private List<BreweryListItemResponse> tasteRanked(Long memberId, List<BreweryListItemResponse> all,
                                                       int requestedSize) {
        Preferences preferences = preferencesOf(memberId);

        Map<String, Integer> scoreByBreweryId = new HashMap<>();
        for (BreweryListItemResponse item : all) {
            scoreByBreweryId.put(item.breweryId(), score(item, preferences));
        }

        List<BreweryListItemResponse> ranked = new ArrayList<>(all);
        ranked.sort(Comparator
                .comparingInt((BreweryListItemResponse item) -> scoreByBreweryId.get(item.breweryId()))
                .reversed()
                .thenComparing(BreweryListItemResponse::businessName)
                .thenComparing(BreweryListItemResponse::breweryId));

        if (ranked.size() >= requestedSize) {
            return ranked;
        }
        return fillWithFixedList(ranked, all, requestedSize);
    }

    private List<BreweryListItemResponse> fillWithFixedList(List<BreweryListItemResponse> ranked,
                                                             List<BreweryListItemResponse> all,
                                                             int requestedSize) {
        Set<String> present = new HashSet<>();
        ranked.forEach(item -> present.add(item.breweryId()));

        List<BreweryListItemResponse> filled = new ArrayList<>(ranked);
        for (BreweryListItemResponse item : fixedOrder(all)) {
            if (filled.size() >= requestedSize) {
                break;
            }
            if (present.add(item.breweryId())) {
                filled.add(item);
            }
        }
        return filled;
    }

    /** 시드 순서 → 시드에 없는 나머지는 상호명 가나다순(all의 기존 순서)으로 이어 붙인다. 중복 없음. */
    private List<BreweryListItemResponse> fixedOrder(List<BreweryListItemResponse> all) {
        Map<String, BreweryListItemResponse> byId = new LinkedHashMap<>();
        all.forEach(item -> byId.put(item.breweryId(), item));

        List<BreweryListItemResponse> ordered = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String breweryId : fixedBrewerySeed.orderedBreweryIds()) {
            BreweryListItemResponse item = byId.get(breweryId);
            if (item != null && used.add(breweryId)) {
                ordered.add(item);
            }
        }
        for (BreweryListItemResponse item : all) {
            if (used.add(item.breweryId())) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    /**
     * 주종 &gt; 지역 우선순위를 가중치 차이로 반영한다(주종만 일치 2점 &gt; 지역만 일치 1점).
     * 지역 취향이 빈 배열(= 전국)이면 지역 항은 적용하지 않는다(전원 0점 처리하지 않고 항 자체를 뺀다).
     */
    private int score(BreweryListItemResponse item, Preferences preferences) {
        int score = 0;
        boolean liquorMatch = item.liquorTypes().stream()
                .anyMatch(liquorType -> preferences.liquorTypes().contains(liquorType.name()));
        if (liquorMatch) {
            score += LIQUOR_MATCH_SCORE;
        }
        if (!preferences.regions().isEmpty() && preferences.regions().contains(item.region())) {
            score += REGION_MATCH_SCORE;
        }
        return score;
    }

    private Preferences preferencesOf(Long memberId) {
        List<OnboardingPreference> saved = onboardingPreferenceRepository.findByMemberId(memberId);
        return new Preferences(
                valuesOf(saved, PreferenceCategory.LIQUOR_TYPE),
                valuesOf(saved, PreferenceCategory.REGION));
    }

    private Set<String> valuesOf(List<OnboardingPreference> saved, PreferenceCategory category) {
        Set<String> values = new LinkedHashSet<>();
        for (OnboardingPreference preference : saved) {
            if (preference.getCategory() == category) {
                values.add(preference.getValue());
            }
        }
        return values;
    }

    /** 인메모리 페이지 슬라이스({@link BreweryQueryService#searchByAccuracy}와 동일한 방식). */
    private PageResponse<BreweryListItemResponse> slice(List<BreweryListItemResponse> ordered,
                                                         int page, int size) {
        long totalElements = ordered.size();
        int from = Math.min(page * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        return PageResponse.of(ordered.subList(from, to), page, size, totalElements);
    }

    private static int clampPage(int page) {
        return Math.max(0, page);
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 회원의 저장된 취향 스냅샷(주종·지역 원시값 집합). 맛은 저장 시점에 이미 주종으로 매핑되어
     * liquorTypes에 담긴다 — 도수만 이번 매칭 범위 밖이라 담지 않는다. */
    private record Preferences(Set<String> liquorTypes, Set<String> regions) {
    }
}
