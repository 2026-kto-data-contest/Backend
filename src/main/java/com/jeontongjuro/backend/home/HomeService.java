package com.jeontongjuro.backend.home;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.brewery.query.BrewerySearchCondition;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.home.dto.HomeBannerResponse;
import com.jeontongjuro.backend.home.dto.HomeBannerType;
import com.jeontongjuro.backend.home.dto.HomeBrewerySectionResponse;
import com.jeontongjuro.backend.home.dto.HomeHeaderResponse;
import com.jeontongjuro.backend.home.dto.HomeResponse;
import com.jeontongjuro.backend.home.dto.HomeViewerResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.recommendation.RecommendedBreweryService;
import com.jeontongjuro.backend.recommendation.RecommendedCourseListService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    static final String DEFAULT_REGION = "수도권";
    static final String DEFAULT_LIQUOR_TYPE = "탁주";
    static final int LIQUOR_SECTION_SIZE = 3;
    static final int REGION_SECTION_SIZE = 3;
    static final int RECOMMENDED_SECTION_SIZE = 6;

    private final BreweryQueryService breweryQueryService;
    private final MemberRepository memberRepository;
    private final RecommendedCourseListService recommendedCourseListService;
    private final RecommendedBreweryService recommendedBreweryService;

    public HomeResponse getHome(Long memberId, String region, String liquorType) {
        String selectedRegion = defaultIfBlank(region, DEFAULT_REGION);
        String selectedLiquorType = defaultIfBlank(liquorType, DEFAULT_LIQUOR_TYPE);
        Member member = findMember(memberId);

        List<BreweryListItemResponse> liquorBreweries = search(
                BrewerySearchCondition.of(null, null, null, null,
                        List.of(selectedLiquorType), null, null),
                LIQUOR_SECTION_SIZE);
        List<BreweryListItemResponse> regionBreweries = search(
                BrewerySearchCondition.of(List.of(selectedRegion), null, null, null,
                        null, null, null),
                REGION_SECTION_SIZE);
        List<BreweryListItemResponse> recommendedBreweries = recommendedBreweryService
                .recommend(memberId, 0, RECOMMENDED_SECTION_SIZE)
                .content();

        return new HomeResponse(
                viewer(member),
                header(member),
                banner(member),
                recommendedCourseListService.homePreview(memberId),
                new HomeBrewerySectionResponse(selectedLiquorType, liquorBreweries),
                new HomeBrewerySectionResponse(selectedRegion, regionBreweries),
                recommendedBreweries);
    }

    private List<BreweryListItemResponse> search(BrewerySearchCondition condition, int size) {
        PageResponse<BreweryListItemResponse> page = breweryQueryService.search(condition, 0, size);
        return page.content();
    }

    private Member findMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository.findById(memberId).orElse(null);
    }

    private HomeViewerResponse viewer(Member member) {
        return new HomeViewerResponse(member != null,
                member != null && member.isOnboardingCompleted());
    }

    private HomeHeaderResponse header(Member member) {
        if (member == null) {
            return new HomeHeaderResponse("전통주 여행을 시작해 볼까요?");
        }
        if (!member.isOnboardingCompleted()) {
            return new HomeHeaderResponse("취향을 알려주시면 더 잘 추천해 드릴게요.");
        }
        return new HomeHeaderResponse(member.getNickname() + "님을 위한 양조장을 추천해 드릴게요.");
    }

    private HomeBannerResponse banner(Member member) {
        if (member == null) {
            return new HomeBannerResponse(HomeBannerType.LOGIN,
                    "로그인하고 나만의 전통주 여행을 시작해 보세요.", "/login");
        }
        if (!member.isOnboardingCompleted()) {
            return new HomeBannerResponse(HomeBannerType.ONBOARDING,
                    "취향을 등록하고 맞춤 양조장을 추천받아 보세요.", "/onboarding");
        }
        return new HomeBannerResponse(HomeBannerType.PERSONALIZED,
                "내 취향에 맞는 양조장과 코스를 만나보세요.", null);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }
}
