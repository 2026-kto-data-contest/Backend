package com.jeontongjuro.backend.search.recent;

import com.jeontongjuro.backend.auth.exception.AuthException;
import com.jeontongjuro.backend.member.Member;
import com.jeontongjuro.backend.member.MemberRepository;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.search.recent.dto.RecentSearchResponse;
import com.jeontongjuro.backend.search.recent.dto.RecentSearchSaveRequest;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecentSearchService {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 10;

    private static final Pattern BREWERY_ID = Pattern.compile("BRW-\\d{3}");
    private static final Pattern PRODUCT_ID = Pattern.compile("PRD-(\\d{4})");
    private static final Set<String> REGIONS = Set.of(
            "수도권", "강원", "충청", "전라", "경상", "부산", "울산", "제주");

    private final RecentSearchRepository recentSearchRepository;
    private final MemberRepository memberRepository;
    private final ProductBreweryLinkRepository productBreweryLinkRepository;

    @Transactional
    public RecentSearchResponse save(Long memberId, RecentSearchSaveRequest request) {
        String targetId = request.id().strip();
        String keyword = request.keyword().strip();
        String displayName = request.displayName().strip();
        validate(request.type(), targetId, keyword, displayName);

        RecentSearch recentSearch = recentSearchRepository
                .findByMemberIdAndTypeAndTargetId(memberId, request.type(), targetId)
                .map(existing -> {
                    existing.refresh(keyword, displayName);
                    return existing;
                })
                .orElseGet(() -> RecentSearch.create(
                        findMember(memberId), request.type(), targetId, keyword, displayName));

        RecentSearch saved = recentSearchRepository.save(recentSearch);
        pruneAfterTenth(memberId);
        return RecentSearchResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RecentSearchResponse> getRecentSearches(Long memberId, int limit) {
        validateLimit(limit);
        return recentSearchRepository
                .findByMemberIdOrderBySearchedAtDescIdDesc(memberId, PageRequest.of(0, limit))
                .stream()
                .map(RecentSearchResponse::from)
                .toList();
    }

    @Transactional
    public void deleteOne(Long memberId, Long recentSearchId) {
        RecentSearch recentSearch = recentSearchRepository.findByIdAndMemberId(recentSearchId, memberId)
                .orElseThrow(() -> new RecentSearchException(
                        HttpStatus.NOT_FOUND,
                        "RECENT_SEARCH_NOT_FOUND",
                        "최근 검색 기록을 찾을 수 없습니다."));
        recentSearchRepository.delete(recentSearch);
    }

    @Transactional
    public void deleteAll(Long memberId) {
        recentSearchRepository.deleteAllByMemberId(memberId);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND",
                        "회원 정보를 찾을 수 없습니다."));
    }

    private void pruneAfterTenth(Long memberId) {
        List<RecentSearch> recentSearches = recentSearchRepository
                .findByMemberIdOrderBySearchedAtDescIdDesc(memberId, PageRequest.of(0, MAX_LIMIT + 1));
        if (recentSearches.size() > MAX_LIMIT) {
            recentSearchRepository.deleteAll(recentSearches.subList(MAX_LIMIT, recentSearches.size()));
        }
    }

    private void validate(RecentSearchType type, String targetId, String keyword, String displayName) {
        if (keyword.isBlank() || displayName.isBlank()) {
            throw invalid("검색어와 표시명은 공백일 수 없습니다.");
        }
        if (keyword.length() > 20) {
            throw invalid("검색어는 최대 20자까지 입력할 수 있습니다.");
        }
        switch (type) {
            case BREWERY -> {
                if (!BREWERY_ID.matcher(targetId).matches()) {
                    throw invalid("양조장 ID는 BRW-xxx 형식이어야 합니다.");
                }
            }
            case PRODUCT -> validateProductId(targetId);
            case REGION -> {
                if (!REGIONS.contains(targetId) || !targetId.equals(displayName)) {
                    throw invalid("지역 ID와 표시명은 지원하는 8개 지역 중 같은 값이어야 합니다.");
                }
            }
        }
    }

    private void validateProductId(String targetId) {
        Matcher matcher = PRODUCT_ID.matcher(targetId);
        if (!matcher.matches()) {
            throw invalid("제품 ID는 PRD-xxxx 형식이어야 합니다.");
        }
        int sourceRowIndex = Integer.parseInt(matcher.group(1));
        if (!productBreweryLinkRepository.existsBySourceRowRef(sourceRowIndex)) {
            throw invalid("존재하지 않는 제품 ID입니다.");
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid("limit은 1 이상 10 이하여야 합니다.");
        }
    }

    private RecentSearchException invalid(String message) {
        return new RecentSearchException(HttpStatus.BAD_REQUEST, "INVALID_RECENT_SEARCH", message);
    }
}
