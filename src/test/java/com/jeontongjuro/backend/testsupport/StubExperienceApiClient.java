package com.jeontongjuro.backend.testsupport;

import com.jeontongjuro.backend.experience.ExperienceApiClient;
import com.jeontongjuro.backend.experience.ExperienceApiException;
import com.jeontongjuro.backend.experience.ExperienceRow;
import java.util.List;
import java.util.function.Supplier;

/**
 * 제어 가능한 체험 API 스텁 — 라이브 odcloud 호출 없이 15단계 diff·skip·fail-fast 경로를 결정론적으로 고정한다.
 * 기본은 주입된 행을 반환하고, 테스트가 {@link #returning}/{@link #failing}으로 동작을 바꾼다.
 * <ul>
 *   <li>{@link #returning} → 성공 응답(그 행들). 시드 미매칭·키중복 fail-fast 경로 재현에 사용.</li>
 *   <li>{@link #failing} → {@link ExperienceApiException} 발생. odcloud 호출 실패 → 15단계 skip 경로 재현.</li>
 * </ul>
 */
public class StubExperienceApiClient implements ExperienceApiClient {

    private Supplier<List<ExperienceRow>> behavior;

    public StubExperienceApiClient(List<ExperienceRow> initialRows) {
        this.behavior = () -> initialRows;
    }

    /** 다음 호출부터 이 행들을 성공 반환한다. */
    public void returning(List<ExperienceRow> rows) {
        this.behavior = () -> rows;
    }

    /** 다음 호출부터 API 실패(ExperienceApiException)를 던진다 — 15단계 skip 경로 재현. */
    public void failing(String message) {
        this.behavior = () -> {
            throw new ExperienceApiException(message);
        };
    }

    @Override
    public List<ExperienceRow> fetchAll() {
        return behavior.get();
    }
}
