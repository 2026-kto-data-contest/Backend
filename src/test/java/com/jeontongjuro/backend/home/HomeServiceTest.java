package com.jeontongjuro.backend.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.home.dto.HomeBannerType;
import com.jeontongjuro.backend.home.dto.HomeResponse;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.recommendation.RecommendedBreweryService;
import com.jeontongjuro.backend.recommendation.RecommendedCourseCardResponse;
import com.jeontongjuro.backend.recommendation.RecommendedCourseListService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private BreweryQueryService breweryQueryService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RecommendedCourseListService recommendedCourseListService;
    @Mock
    private RecommendedBreweryService recommendedBreweryService;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(breweryQueryService, memberRepository,
                recommendedCourseListService, recommendedBreweryService);
        given(breweryQueryService.search(any(), anyInt(), anyInt()))
                .willAnswer(invocation -> PageResponse.of(
                        List.of(), 0, invocation.getArgument(2), 0L));
        given(recommendedCourseListService.homePreview(any())).willReturn(List.of(
                new RecommendedCourseCardResponse("BRW-001", null, "충북 영동", "갈기산 코스")));
        given(recommendedBreweryService.recommend(any(), anyInt(), anyInt()))
                .willAnswer(invocation -> PageResponse.of(
                        List.of(), invocation.getArgument(1), invocation.getArgument(2), 0L));
    }

    @Test
    void anonymousUsesDefaultsAndLoginBanner() {
        HomeResponse response = homeService.getHome(null, null, null);

        assertThat(response.viewer().authenticated()).isFalse();
        assertThat(response.viewer().onboardingCompleted()).isFalse();
        assertThat(response.banner().type()).isEqualTo(HomeBannerType.LOGIN);
        assertThat(response.banner().actionPath()).isEqualTo("/login");
        assertThat(response.liquorTypeBreweries().selectedValue()).isEqualTo("탁주");
        assertThat(response.regionBreweries().selectedValue()).isEqualTo("수도권");
        assertThat(response.recommendedCourses()).hasSize(1);
        assertThat(response.recommendedCourses().get(0).courseId()).isEqualTo("BRW-001");
        assertThat(response.recommendedBreweries()).isEmpty();
    }

    @Test
    void onboardingIncompleteMemberUsesOnboardingBanner() {
        Member member = Member.createKakao(1L, "수빈", "subin@example.com");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        HomeResponse response = homeService.getHome(1L, "전라", "약주");

        assertThat(response.viewer().authenticated()).isTrue();
        assertThat(response.viewer().onboardingCompleted()).isFalse();
        assertThat(response.banner().type()).isEqualTo(HomeBannerType.ONBOARDING);
        assertThat(response.banner().actionPath()).isEqualTo("/onboarding");
        assertThat(response.liquorTypeBreweries().selectedValue()).isEqualTo("약주");
        assertThat(response.regionBreweries().selectedValue()).isEqualTo("전라");
    }

    @Test
    void onboardingCompleteMemberUsesPersonalizedBannerAndRecommendationOrder() {
        Member member = Member.createKakao(1L, "수빈", "subin@example.com");
        member.completeOnboarding();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        HomeResponse response = homeService.getHome(1L, null, null);

        assertThat(response.viewer().authenticated()).isTrue();
        assertThat(response.viewer().onboardingCompleted()).isTrue();
        assertThat(response.header().message()).contains("수빈님");
        assertThat(response.banner().type()).isEqualTo(HomeBannerType.PERSONALIZED);
        assertThat(response.banner().actionPath()).isNull();
    }
}
