package com.jeontongjuro.backend.global.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이징 응답 공용 래퍼(2인 공용, global 소속).
 * <p>
 * ★Spring {@link Page}를 그대로 직렬화하지 않는다 — Page의 JSON 형태는 버전에 따라 흔들려
 * 클라이언트 계약이 불안정하다. 필요한 메타(page·size·totalElements·totalPages)만 명시 필드로 고정한다.
 *
 * @param content       현재 페이지 아이템 목록(응답 DTO)
 * @param page          현재 페이지 번호(0-based)
 * @param size          페이지 크기
 * @param totalElements 필터 적용 후 전체 건수
 * @param totalPages    전체 페이지 수
 */
public record PageResponse<T>(
        @Schema(description = "현재 페이지에 포함된 데이터 목록") List<T> content,
        @Schema(description = "현재 페이지 번호. 0부터 시작", example = "0") int page,
        @Schema(description = "요청한 페이지 크기", example = "20") int size,
        @Schema(description = "필터를 적용한 전체 데이터 개수", example = "42") long totalElements,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages) {

    /** Spring {@link Page}에서 메타를 추출하고, content는 이미 매핑된 DTO 목록으로 감싼다. */
    public static <T> PageResponse<T> of(List<T> content, Page<?> source) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
