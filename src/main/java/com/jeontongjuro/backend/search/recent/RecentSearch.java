package com.jeontongjuro.backend.search.recent;

import com.jeontongjuro.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "recent_search",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_recent_search_member_target",
                columnNames = {"member_id", "search_type", "target_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type", nullable = false, columnDefinition = "text")
    private RecentSearchType type;

    @Column(name = "target_id", nullable = false, columnDefinition = "text")
    private String targetId;

    @Column(nullable = false, columnDefinition = "text")
    private String keyword;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    public static RecentSearch create(
            Member member,
            RecentSearchType type,
            String targetId,
            String keyword,
            String displayName
    ) {
        RecentSearch recentSearch = new RecentSearch();
        recentSearch.member = member;
        recentSearch.type = type;
        recentSearch.targetId = targetId;
        recentSearch.refresh(keyword, displayName);
        return recentSearch;
    }

    public void refresh(String keyword, String displayName) {
        this.keyword = keyword;
        this.displayName = displayName;
        this.searchedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
