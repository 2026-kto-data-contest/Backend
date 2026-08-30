package com.jeontongjuro.backend.search.recent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.search.recent.dto.RecentSearchResponse;
import com.jeontongjuro.backend.search.recent.dto.RecentSearchSaveRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class RecentSearchServiceTest {

    private RecentSearchRepository recentSearchRepository;
    private MemberRepository memberRepository;
    private ProductBreweryLinkRepository productBreweryLinkRepository;
    private RecentSearchService recentSearchService;
    private Member member;

    @BeforeEach
    void setUp() {
        recentSearchRepository = mock(RecentSearchRepository.class);
        memberRepository = mock(MemberRepository.class);
        productBreweryLinkRepository = mock(ProductBreweryLinkRepository.class);
        recentSearchService = new RecentSearchService(
                recentSearchRepository, memberRepository, productBreweryLinkRepository);
        member = Member.createKakao(100L, "수빈", "subin@example.com");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(recentSearchRepository.save(any(RecentSearch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(recentSearchRepository.findByMemberIdOrderBySearchedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(productBreweryLinkRepository.existsBySourceRowRef(3)).thenReturn(true);
        when(productBreweryLinkRepository.existsBySourceRowRef(9999)).thenReturn(true);
    }

    @Test
    void firstSearchCreatesRecentSearch() {
        RecentSearchResponse response = recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(RecentSearchType.BREWERY, " BRW-001 ", " 갈기산 ", " 갈기산 "));

        assertThat(response.type()).isEqualTo(RecentSearchType.BREWERY);
        assertThat(response.id()).isEqualTo("BRW-001");
        assertThat(response.keyword()).isEqualTo("갈기산");
        assertThat(response.displayName()).isEqualTo("갈기산");
        assertThat(response.searchedAt()).isNotNull();
        assertThat(response.searchedAt().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    void sameTypeAndTargetRefreshesExistingRow() {
        RecentSearch existing = RecentSearch.create(
                member, RecentSearchType.PRODUCT, "PRD-0003", "옛 검색어", "옛 제품명");
        when(recentSearchRepository.findByMemberIdAndTypeAndTargetId(
                1L, RecentSearchType.PRODUCT, "PRD-0003"))
                .thenReturn(Optional.of(existing));

        RecentSearchResponse response = recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(
                        RecentSearchType.PRODUCT, "PRD-0003", "새 검색어", "새 제품명"));

        assertThat(response.keyword()).isEqualTo("새 검색어");
        assertThat(response.displayName()).isEqualTo("새 제품명");
        verify(recentSearchRepository).save(existing);
    }

    @Test
    void eleventhEntryPrunesOldestRow() {
        List<RecentSearch> eleven = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            eleven.add(RecentSearch.create(
                    member, RecentSearchType.BREWERY, "BRW-%03d".formatted(i), "검색어" + i, "양조장" + i));
        }
        when(recentSearchRepository.findByMemberIdOrderBySearchedAtDescIdDesc(
                eq(1L), any(Pageable.class))).thenReturn(eleven);

        recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(RecentSearchType.BREWERY, "BRW-012", "검색어12", "양조장12"));

        verify(recentSearchRepository).deleteAll(eleven.subList(10, 11));
    }

    @Test
    void invalidTypeSpecificIdIsRejected() {
        assertThatThrownBy(() -> recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(RecentSearchType.BREWERY, "PRD-0003", "갈기산", "갈기산")))
                .isInstanceOf(RecentSearchException.class)
                .hasMessageContaining("BRW-xxx");

        assertThatThrownBy(() -> recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(RecentSearchType.REGION, "충청", "충청", "전라")))
                .isInstanceOf(RecentSearchException.class)
                .hasMessageContaining("같은 값");
    }

    @Test
    void productIdMustExistButHasNoHardCodedUpperBound() {
        RecentSearchResponse response = recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(
                        RecentSearchType.PRODUCT, "PRD-9999", "신제품", "새 제품명"));

        assertThat(response.id()).isEqualTo("PRD-9999");

        assertThatThrownBy(() -> recentSearchService.save(
                1L,
                new RecentSearchSaveRequest(
                        RecentSearchType.PRODUCT, "PRD-9998", "없는 제품", "없는 제품")))
                .isInstanceOf(RecentSearchException.class)
                .hasMessageContaining("존재하지 않는 제품");
    }

    @Test
    void limitMustBeBetweenOneAndTen() {
        assertThatThrownBy(() -> recentSearchService.getRecentSearches(1L, 0))
                .isInstanceOf(RecentSearchException.class);
        assertThatThrownBy(() -> recentSearchService.getRecentSearches(1L, 11))
                .isInstanceOf(RecentSearchException.class);
    }
}
