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

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(breweryQueryService, memberRepository);
        given(breweryQueryService.search(any(), anyInt(), anyInt()))
                .willAnswer(invocation -> PageResponse.of(
                        List.of(), 0, invocation.getArgument(2), 0L));
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
        assertThat(response.recommendedCourses()).isEmpty();
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
    void onboardingCompleteMemberUsesDefaultBannerUntilPersonalizationExists() {
        Member member = Member.createKakao(1L, "수빈", "subin@example.com");
        member.completeOnboarding();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        HomeResponse response = homeService.getHome(1L, null, null);

        assertThat(response.viewer().authenticated()).isTrue();
        assertThat(response.viewer().onboardingCompleted()).isTrue();
        assertThat(response.header().message()).contains("수빈님");
        assertThat(response.banner().type()).isEqualTo(HomeBannerType.DEFAULT);
        assertThat(response.banner().actionPath()).isNull();
    }
}
