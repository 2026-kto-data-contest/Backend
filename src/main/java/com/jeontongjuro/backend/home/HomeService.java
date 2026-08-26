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
        List<BreweryListItemResponse> recommendedBreweries = search(
                BrewerySearchCondition.of(null, null, null, null,
                        null, null, null),
                RECOMMENDED_SECTION_SIZE);

        return new HomeResponse(
                viewer(member),
                header(member),
                banner(member),
                List.of(),
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
        // 취향 저장·추천 로직이 연결되기 전까지 개인화된 것처럼 표시하지 않는다.
        return new HomeBannerResponse(HomeBannerType.DEFAULT,
                "새로운 전통주 양조장을 만나보세요.", null);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }
}
