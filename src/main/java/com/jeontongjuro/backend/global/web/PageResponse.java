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
        @Schema(description = "현재 페이지에 포함된 데이터 목록. 비어 있어도 null이 아니라 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED) List<T> content,
        @Schema(description = "현재 페이지 번호. 0부터 시작", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(description = "요청한 페이지 크기", example = "20",
                requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(description = "필터를 적용한 전체 데이터 개수", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(description = "전체 페이지 수", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    /** Spring {@link Page}에서 메타를 추출하고, content는 이미 매핑된 DTO 목록으로 감싼다. */
    public static <T> PageResponse<T> of(List<T> content, Page<?> source) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }

    /**
     * 인메모리 페이지네이션용 팩토리 — Spring {@link Page} 없이 계산한 메타로 감싼다. 전체를 메모리에서 정렬·
     * 병합한 뒤 슬라이스하는 조회(제품 목록)에서 쓴다. totalPages는 totalElements/size의 올림.
     *
     * @param content       현재 페이지에 담을(이미 슬라이스된) DTO 목록
     * @param page          현재 페이지 번호(0-based, 클램프 후)
     * @param size          페이지 크기(클램프 후, 1 이상)
     * @param totalElements 필터·제외·병합을 모두 반영한 전체 건수
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size < 1 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
