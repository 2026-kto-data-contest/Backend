package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.experience.BreweryExperience;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 양조장 체험 프로그램 1건(상세 응답 {@code experiences[]} 원소, #52 additive). 상세 API 전용 — 리스트 API엔
 * 노출하지 않는다(스캔용). 저장 5필드만 노출하며 주소·연락처·홈페이지·주종·방문여부는 애초에 저장하지 않는다.
 * <p>
 * cost는 0(무료)과 null(미입력)을 그대로 노출한다 — 프론트가 0은 "무료", null은 "정보 없음"으로 표기.
 * duration은 원문 "H:MM"(예 "2:00"·"8:00") 그대로다.
 */
public record ExperienceResponse(
        @Schema(description = "체험 프로그램명", example = "누룩만들기",
                requiredMode = Schema.RequiredMode.REQUIRED) String programName,
        @Schema(description = "체험 내용 설명. 없으면 null", example = "전통 누룩을 직접 디뎌 만드는 체험",
                nullable = true) String content,
        @Schema(description = "체험 장소. 없으면 null", example = "명인 안동소주 양조장", nullable = true) String place,
        @Schema(description = "소요시간 원문 H:MM. 없으면 null", example = "2:00", nullable = true) String duration,
        @Schema(description = "체험 비용(원). 0=무료·null=정보 없음(둘을 구분)", example = "20000",
                nullable = true) Integer cost) {

    public static ExperienceResponse from(BreweryExperience e) {
        return new ExperienceResponse(
                e.getProgramName(), e.getContent(), e.getPlace(), e.getDuration(), e.getCost());
    }
}
