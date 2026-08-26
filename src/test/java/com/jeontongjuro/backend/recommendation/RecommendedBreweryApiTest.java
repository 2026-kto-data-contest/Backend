package com.jeontongjuro.backend.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.brewery.query.BrewerySearchCondition;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.onboarding.OnboardingPreference;
import com.jeontongjuro.backend.onboarding.OnboardingPreferenceRepository;
import com.jeontongjuro.backend.onboarding.PreferenceCategory;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 추천 양조장 API(GET /api/v1/recommendations/breweries) 검증.
 * <p>
 * ★{@link BreweryQueryService}는 {@link MockitoBean}으로 대체한다 — 실 {@code brewery}·
 * {@code product_liquor_type} 테이블은 파이프라인 계열 테스트(예: ProcessOrchestratorIntegrationTest)가
 * 전체 스위트 실행 중 재적재·변형해 골든 값(59곳·지역별 분포 등)이 실행 시점마다 달라질 수 있다
 * (부채: "골든을 픽스처에 억지로 맞추지 마라"). 이 서비스의 알고리즘(정렬·클램프·페이징)은 브루어리
 * 데이터 내용과 무관하므로, 통제된 합성 데이터로 검증해 파이프라인 테스트와의 실행 순서 의존성을 없앤다.
 * <p>
 * ★취향 픽스처(member_account·member_preference)는 수빈 엔티티를 읽기만 해서 만든다 — 엔티티 자체는
 * 수정하지 않는다. 두 테이블은 파이프라인이 건드리지 않는 실 DB 테이블이라 그대로 사용한다.
 * <p>
 * 🔴취향 기반 정렬(③)은 member_preference 실 데이터가 0건이라 라이브 curl로 검증할 수 없다(정찰 산출) —
 * 이 클래스가 유일한 검증 경로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 추천 양조장 API verify 스킵")
class RecommendedBreweryApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OnboardingPreferenceRepository onboardingPreferenceRepository;
    @MockitoBean
    private BreweryQueryService breweryQueryService;

    private final Set<Long> createdMemberIds = new HashSet<>();

    @AfterEach
    void tearDown() {
        // ★deleteByMemberId(derived 벌크 삭제)는 명시 트랜잭션이 필요해 여기(비-@Transactional 컨텍스트)선
        // 쓰지 않는다 — 상속받은 CRUD 메서드(findByMemberId+deleteAll)는 SimpleJpaRepository가 자체
        // 트랜잭션을 열어 처리하므로 그대로 쓸 수 있다(RecentSearchApiTest의 deleteAll() 패턴과 동일).
        createdMemberIds.forEach(id -> {
            onboardingPreferenceRepository.deleteAll(onboardingPreferenceRepository.findByMemberId(id));
            memberRepository.findById(id).ifPresent(memberRepository::delete);
        });
        createdMemberIds.clear();
    }

    @Test
    void anonymousGetsFixedOrder() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "수도권", LiquorType.탁주),
                brewery("BRW-B", "나양조", "충청", LiquorType.약주),
                brewery("BRW-C", "다양조", "전라", LiquorType.청주),
                brewery("BRW-D", "라양조", "경상", LiquorType.증류주),
                brewery("BRW-E", "마양조", "강원", LiquorType.과실주),
                brewery("BRW-F", "바양조", "제주", LiquorType.탁주),
                brewery("BRW-G", "사양조", "부산", LiquorType.약주)));

        mockMvc.perform(get("/api/v1/recommendations/breweries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(6))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content.length()").value(6))
                // 시드가 비어 있으므로 상호명 가나다순 그대로.
                .andExpect(jsonPath("$.content[0].breweryId").value("BRW-A"))
                .andExpect(jsonPath("$.content[5].breweryId").value("BRW-F"));
    }

    @Test
    void memberWithoutOnboardingGetsSameFixedOrderAsAnonymous() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "수도권", LiquorType.탁주),
                brewery("BRW-B", "나양조", "충청", LiquorType.약주),
                brewery("BRW-C", "다양조", "전라", LiquorType.청주)));
        Member member = createMember(910000001L, "온보딩 전 회원");

        String anonymous = mockMvc.perform(get("/api/v1/recommendations/breweries"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String loggedIn = mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(loggedIn).isEqualTo(anonymous);
    }

    @Test
    void liquorMatchRanksAboveRegionMatch() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "충청", LiquorType.탁주),   // 주종만 일치 → 2점
                brewery("BRW-B", "나양조", "제주")));                  // 지역만 일치 → 1점
        Member member = createOnboardedMember(910000002L, "취향 회원");
        savePreference(member, PreferenceCategory.LIQUOR_TYPE, "탁주");
        savePreference(member, PreferenceCategory.REGION, "제주");

        mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].breweryId").value("BRW-A"))
                .andExpect(jsonPath("$.content[1].breweryId").value("BRW-B"));
    }

    @Test
    void bothAxesMatchRanksHighest() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "충청", LiquorType.탁주),               // 주종만 → 2점
                brewery("BRW-B", "나양조", "제주"),                                // 지역만 → 1점
                brewery("BRW-C", "다양조", "제주", LiquorType.탁주)));             // 둘 다 → 3점
        Member member = createOnboardedMember(910000003L, "완전일치 회원");
        savePreference(member, PreferenceCategory.LIQUOR_TYPE, "탁주");
        savePreference(member, PreferenceCategory.REGION, "제주");

        mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].breweryId").value("BRW-C"));
    }

    @Test
    void zeroScoreBreweriesAreStillIncludedNotFiltered() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "제주", LiquorType.탁주),  // 3점
                brewery("BRW-B", "나양조", "충청")));                 // 0점(주종·지역 둘 다 불일치)
        Member member = createOnboardedMember(910000004L, "필터아님 회원");
        savePreference(member, PreferenceCategory.LIQUOR_TYPE, "탁주");
        savePreference(member, PreferenceCategory.REGION, "제주");

        mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(member)))
                .andExpect(status().isOk())
                // 필터가 아니라 정렬 — 0점인 BRW-B도 결과에 포함되고 totalElements도 전체 2건.
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].breweryId").value("BRW-B"));
    }

    @Test
    void emptyRegionPreferenceMeansNationwideNotZeroScore() throws Exception {
        // BRW-A가 상호명 가나다순으로 더 앞이지만 지역은 불일치, BRW-B는 순서상 뒤지만 지역이 일치한다.
        // 지역 축을 적용하면 BRW-B가 앞으로 올라오고(3점>2점), 지역 축을 빼면 둘 다 2점 동점이라
        // 안정 정렬이 입력 순서(상호명순=A,B)를 그대로 유지한다 — 두 결과가 달라야 지역 축 on/off가 증명된다.
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "제주", LiquorType.탁주),
                brewery("BRW-B", "나양조", "충청", LiquorType.탁주),
                brewery("BRW-C", "다양조", "제주")));

        Member withRegion = createOnboardedMember(910000005L, "지역선택 회원");
        savePreference(withRegion, PreferenceCategory.LIQUOR_TYPE, "탁주");
        savePreference(withRegion, PreferenceCategory.REGION, "충청");

        Member withoutRegion = createOnboardedMember(910000006L, "지역미선택 회원");
        savePreference(withoutRegion, PreferenceCategory.LIQUOR_TYPE, "탁주");
        // REGION 미저장 = 전국(빈 배열) — 지역 항을 아예 적용하지 않아야 한다.

        String withRegionBody = mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(withRegion)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String withoutRegionBody = mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(withoutRegion)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 지역 선택 여부만 다르므로 순서가 달라야 한다(지역 미선택은 주종만으로 정렬 — 전원 0점 처리가 아님을 증명).
        Assertions.assertThat(withRegionBody).isNotEqualTo(withoutRegionBody);
    }

    @Test
    void tiesBreakByBusinessNameAscending() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "다양조", "충청", LiquorType.탁주),
                brewery("BRW-B", "가양조", "충청", LiquorType.탁주),
                brewery("BRW-C", "나양조", "충청", LiquorType.탁주)));
        Member member = createOnboardedMember(910000007L, "동점 회원");
        savePreference(member, PreferenceCategory.LIQUOR_TYPE, "탁주");

        // 셋 다 동점(2점) — 입력 순서(BRW-A,B,C)는 상호명 가나다순이 아니다(다양조,가양조,나양조).
        // 동점 시 상호명 가나다순으로 명시 정렬되므로 가양조(BRW-B) → 나양조(BRW-C) → 다양조(BRW-A) 순이어야 한다.
        mockMvc.perform(get("/api/v1/recommendations/breweries").with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].breweryId").value("BRW-B"))
                .andExpect(jsonPath("$.content[1].breweryId").value("BRW-C"))
                .andExpect(jsonPath("$.content[2].breweryId").value("BRW-A"));
    }

    @Test
    void sizeIsClampedTo100AndPageIsClampedToZero() throws Exception {
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "수도권", LiquorType.탁주),
                brewery("BRW-B", "나양조", "충청", LiquorType.약주),
                brewery("BRW-C", "다양조", "전라", LiquorType.청주)));

        mockMvc.perform(get("/api/v1/recommendations/breweries")
                        .param("size", "200")
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void pagingHasNoDuplicatesOrGaps() throws Exception {
        List<BreweryListItemResponse> population = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            population.add(brewery("BRW-%02d".formatted(i), "양조장%02d".formatted(i), "충청", LiquorType.탁주));
        }
        stubAllBreweries(population);

        Set<String> seen = new HashSet<>();
        for (int page = 0; page <= 2; page++) {
            String body = mockMvc.perform(get("/api/v1/recommendations/breweries")
                            .param("size", "20")
                            .param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode root = new ObjectMapper().readTree(body);
            for (JsonNode item : root.get("content")) {
                String id = item.get("breweryId").asText();
                Assertions.assertThat(seen.add(id)).as("페이지 간 중복 없어야 함: " + id).isTrue();
            }
        }
        Assertions.assertThat(seen).hasSize(45);
    }

    @Test
    void noDuplicatesBetweenTasteRankedAndFixedListFill() throws Exception {
        // 부족분 채우기(§3-3) 실행 경로: 요청 size(10)가 모집단(3곳)보다 큰 경우에만 발동한다.
        // 고정 목록도 같은 모집단에서 뽑으므로 채우기 후에도 중복이 없어야 한다(항목 수는 그대로 3).
        stubAllBreweries(List.of(
                brewery("BRW-A", "가양조", "충청", LiquorType.탁주),
                brewery("BRW-B", "나양조", "제주", LiquorType.탁주),
                brewery("BRW-C", "다양조", "제주")));
        Member member = createOnboardedMember(910000008L, "부족분 회원");
        savePreference(member, PreferenceCategory.LIQUOR_TYPE, "탁주");

        mockMvc.perform(get("/api/v1/recommendations/breweries")
                        .param("size", "10")
                        .with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void responseContractIsPageResponse() throws Exception {
        stubAllBreweries(List.of(brewery("BRW-A", "가양조", "수도권", LiquorType.탁주)));

        mockMvc.perform(get("/api/v1/recommendations/breweries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    /** {@link RecommendedBreweryService}가 부르는 조회를 통제된 합성 데이터로 스텁한다(실 DB brewery 미사용). */
    private void stubAllBreweries(List<BreweryListItemResponse> allInAlphabeticalOrder) {
        given(breweryQueryService.search(any(BrewerySearchCondition.class), eq(0), eq(100)))
                .willReturn(PageResponse.of(allInAlphabeticalOrder, 0, 100, allInAlphabeticalOrder.size()));
    }

    private BreweryListItemResponse brewery(String breweryId, String businessName, String region,
                                            LiquorType... liquorTypes) {
        return new BreweryListItemResponse(
                breweryId, businessName, null, region,
                VisitState.UNKNOWN, VisitState.UNKNOWN,
                List.of(), null, null,
                List.of(liquorTypes), null, null, List.of(), null);
    }

    private Member createMember(Long kakaoUserId, String nickname) {
        Member member = memberRepository.findByKakaoUserId(kakaoUserId)
                .orElseGet(() -> memberRepository.save(
                        Member.createKakao(kakaoUserId, nickname, nickname + "@example.com")));
        createdMemberIds.add(member.getId());
        return member;
    }

    /** 온보딩 후(state ③) 픽스처 — {@link Member#completeOnboarding()}은 기존 공개 메서드를 호출만 한다(엔티티 미수정). */
    private Member createOnboardedMember(Long kakaoUserId, String nickname) {
        Member member = createMember(kakaoUserId, nickname);
        member.completeOnboarding();
        return memberRepository.save(member);
    }

    private void savePreference(Member member, PreferenceCategory category, String value) {
        onboardingPreferenceRepository.save(OnboardingPreference.create(member, category, value));
    }

    private RequestPostProcessor auth(Member target) {
        AuthenticatedMember principal = new AuthenticatedMember(
                target.getId(), target.getEmail(), target.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + target.getRole().name()))));
    }
}
