package com.jeontongjuro.backend.experience;

import java.util.List;

/**
 * aT 체험 프로그램 원본 조회 계약. 구현은 라이브 odcloud({@link ExperienceApiClientImpl}), 테스트는 스텁
 * (라이브 RestClient 역직렬화 경로를 우회해 diff·skip 로직만 고정 — collect FixtureRawSnapshotSource 선례).
 * <p>
 * ★호출/파싱 실패는 {@link ExperienceApiException}으로 던진다(15단계 skip 사유). 성공 시 전체 행을 원본 순서로
 * 반환한다(페이지 순서 보존). 반환 행수·distinct 양조장 수는 호출자가 로그·리포트로 남긴다.
 */
public interface ExperienceApiClient {

    /** 체험 프로그램 전체 행을 원본(페이지) 순서로 조회. 실패 시 {@link ExperienceApiException}. */
    List<ExperienceRow> fetchAll();
}
