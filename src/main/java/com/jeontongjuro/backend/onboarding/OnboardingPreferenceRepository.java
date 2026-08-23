package com.jeontongjuro.backend.onboarding;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OnboardingPreferenceRepository extends JpaRepository<OnboardingPreference, Long> {

    List<OnboardingPreference> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    @Query("""
            select count(preference)
            from OnboardingPreference preference
            where preference.member.id = :memberId and preference.category = :category
            """)
    long countByMemberIdAndCategory(Long memberId, PreferenceCategory category);

    default boolean hasAllRequiredCategories(Long memberId) {
        return countByMemberIdAndCategory(memberId, PreferenceCategory.LIQUOR_TYPE) > 0
                && countByMemberIdAndCategory(memberId, PreferenceCategory.ALCOHOL_LEVEL) == 1;
    }
}
