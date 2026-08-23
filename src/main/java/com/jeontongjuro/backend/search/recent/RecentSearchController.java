package com.jeontongjuro.backend.search.recent;

import com.jeontongjuro.backend.search.recent.dto.RecentSearchResponse;
import com.jeontongjuro.backend.search.recent.dto.RecentSearchSaveRequest;
import com.jeontongjuro.backend.security.session.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색", description = "로그인 회원 최근 검색어 API")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/search/recent")
@RequiredArgsConstructor
public class RecentSearchController {

    private final RecentSearchService recentSearchService;

    @Operation(
            summary = "최근 검색어 저장",
            description = """
                    자동완성 항목을 선택하거나 검색을 확정했을 때 호출합니다.
                    같은 type과 id가 이미 있으면 새 행을 만들지 않고 검색 시각과 표시 스냅샷을 갱신합니다.
                    회원당 최신 10건만 유지하며 비로그인 기록은 프론트 로컬 저장소에서 관리합니다.
                    """)
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 또는 최신화 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 검색 기록"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치")
    })
    @PostMapping
    public RecentSearchResponse save(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody RecentSearchSaveRequest request
    ) {
        return recentSearchService.save(member.id(), request);
    }

    @Operation(
            summary = "최근 검색어 목록",
            description = "최신 검색 순서로 조회합니다. 기본 5건이며 limit은 최대 10입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 limit"),
            @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping
    public List<RecentSearchResponse> getRecentSearches(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return recentSearchService.getRecentSearches(member.id(), limit);
    }

    @Operation(summary = "최근 검색어 개별 삭제", description = "현재 회원의 검색 기록 한 건을 삭제합니다.")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치"),
            @ApiResponse(responseCode = "404", description = "본인 소유의 검색 기록 없음")
    })
    @DeleteMapping("/{recentSearchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long recentSearchId
    ) {
        recentSearchService.deleteOne(member.id(), recentSearchId);
    }

    @Operation(summary = "최근 검색어 전체 삭제", description = "현재 회원의 최근 검색 기록을 모두 삭제합니다.")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "전체 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal AuthenticatedMember member) {
        recentSearchService.deleteAll(member.id());
    }
}
