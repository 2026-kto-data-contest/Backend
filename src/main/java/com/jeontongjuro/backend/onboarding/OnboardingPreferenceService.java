package com.jeontongjuro.backend.onboarding;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.brewery.query.Region;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.onboarding.dto.OnboardingPreferencesRequest;
import com.jeontongjuro.backend.onboarding.dto.OnboardingPreferencesResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingPreferenceService {

    private final OnboardingPreferenceRepository preferenceRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public OnboardingPreferencesResponse get(Long memberId) {
        return response(preferenceRepository.findByMemberId(memberId));
    }

    @Transactional
    public OnboardingPreferencesResponse save(Long memberId, OnboardingPreferencesRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."));

        Set<String> liquorTypes = map(request.liquorTypes(), this::liquorType);
        Set<String> regions = mapOptional(request.regions(), raw -> {
            Region region = Region.from(raw);
            if (region == null) {
                throw invalidPreferences("지역은 공백일 수 없습니다.");
            }
            return region.name();
        });
        String alcoholLevel = AlcoholPreferenceLevel.from(request.alcoholLevel()).name();

        preferenceRepository.deleteByMemberId(memberId);
        preferenceRepository.flush();
        save(member, PreferenceCategory.LIQUOR_TYPE, liquorTypes);
        save(member, PreferenceCategory.REGION, regions);
        save(member, PreferenceCategory.ALCOHOL_LEVEL, Set.of(alcoholLevel));
        return new OnboardingPreferencesResponse(List.copyOf(liquorTypes), List.copyOf(regions), alcoholLevel);
    }

    private Set<String> map(List<String> values, java.util.function.Function<String, String> mapper) {
        if (values == null || values.isEmpty()) {
            throw invalidPreferences("선호 주종은 한 개 이상 선택해야 합니다.");
        }
        Set<String> mapped = new LinkedHashSet<>();
        values.forEach(value -> mapped.add(mapper.apply(value)));
        return mapped;
    }

    private Set<String> mapOptional(List<String> values, java.util.function.Function<String, String> mapper) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> mapped = new LinkedHashSet<>();
        values.forEach(value -> mapped.add(mapper.apply(value)));
        return mapped;
    }

    private void save(Member member, PreferenceCategory category, Set<String> values) {
        preferenceRepository.saveAll(values.stream()
                .map(value -> OnboardingPreference.create(member, category, value))
                .toList());
    }

    private String liquorType(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return LiquorType.valueOf(raw.strip()).name();
            } catch (IllegalArgumentException ignored) {
                // 조회 필터와 달리 온보딩 취향에서는 기능명세서의 '기타'도 허용한다.
            }
        }
        throw invalidPreferences("허용되지 않은 선호 주종입니다: '" + raw
                + "' (허용: 탁주, 약주, 청주, 증류주, 과실주, 기타)");
    }

    private OnboardingPreferencesResponse response(List<OnboardingPreference> preferences) {
        List<String> liquorTypes = values(preferences, PreferenceCategory.LIQUOR_TYPE);
        List<String> regions = values(preferences, PreferenceCategory.REGION);
        String alcoholLevel = values(preferences, PreferenceCategory.ALCOHOL_LEVEL).stream()
                .findFirst().orElse(null);
        return new OnboardingPreferencesResponse(liquorTypes, regions, alcoholLevel);
    }

    private List<String> values(List<OnboardingPreference> preferences, PreferenceCategory category) {
        return preferences.stream()
                .filter(preference -> preference.getCategory() == category)
                .map(OnboardingPreference::getValue)
                .sorted()
                .toList();
    }

    private AuthException invalidPreferences(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ONBOARDING_PREFERENCES", message);
    }
}
