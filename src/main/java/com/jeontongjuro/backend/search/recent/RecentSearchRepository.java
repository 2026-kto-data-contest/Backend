package com.jeontongjuro.backend.search.recent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {

    Optional<RecentSearch> findByMemberIdAndTypeAndTargetId(
            Long memberId, RecentSearchType type, String targetId);

    List<RecentSearch> findByMemberIdOrderBySearchedAtDescIdDesc(Long memberId, Pageable pageable);

    Optional<RecentSearch> findByIdAndMemberId(Long id, Long memberId);

    void deleteAllByMemberId(Long memberId);
}
