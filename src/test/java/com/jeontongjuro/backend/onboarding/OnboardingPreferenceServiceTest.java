package com.jeontongjuro.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.onboarding.dto.OnboardingPreferencesRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OnboardingPreferenceServiceTest {

    @Test
    void replacesPreferencesWithValidatedDistinctValues() {
        OnboardingPreferenceRepository repository = mock(OnboardingPreferenceRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        OnboardingPreferenceService service = new OnboardingPreferenceService(repository, memberRepository);
        Member member = mock(Member.class);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        var response = service.save(10L, new OnboardingPreferencesRequest(
                List.of("탁주", "탁주", "약주", "기타"),
                List.of("수도권", "강원"),
                "MEDIUM"));

        assertThat(response.liquorTypes()).containsExactly("탁주", "약주", "기타");
        assertThat(response.regions()).containsExactly("수도권", "강원");
        assertThat(response.alcoholLevel()).isEqualTo("MEDIUM");
        verify(repository).deleteByMemberId(10L);
        verify(repository).flush();
        verify(repository, org.mockito.Mockito.times(3)).saveAll(anyList());
    }

    @Test
    void rejectsUnknownAlcoholLevelBeforeReplacingStoredPreferences() {
        OnboardingPreferenceRepository repository = mock(OnboardingPreferenceRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        OnboardingPreferenceService service = new OnboardingPreferenceService(repository, memberRepository);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(mock(Member.class)));

        assertThatThrownBy(() -> service.save(10L, new OnboardingPreferencesRequest(
                List.of("탁주"), List.of("수도권"), "VERY_HIGH")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("허용되지 않은 도수 취향");

        verify(repository, org.mockito.Mockito.never()).deleteByMemberId(10L);
    }

    @Test
    void emptyRegionsMeanNationwideAndAreAllowed() {
        OnboardingPreferenceRepository repository = mock(OnboardingPreferenceRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        OnboardingPreferenceService service = new OnboardingPreferenceService(repository, memberRepository);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(mock(Member.class)));

        var response = service.save(10L,
                new OnboardingPreferencesRequest(List.of("과실주"), List.of(), "LIGHT"));

        assertThat(response.regions()).isEmpty();
        assertThat(response.alcoholLevel()).isEqualTo("LIGHT");
    }
}
