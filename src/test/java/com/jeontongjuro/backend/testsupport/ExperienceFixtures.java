package com.jeontongjuro.backend.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.experience.ExperienceRow;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 체험 API 픽스처 로더 — 라이브 odcloud 응답을 저장한 {@code experience_fixture.json}(실측 52행, 30 양조장)의
 * {@code data} 배열을 {@link ExperienceRow}로 파싱한다. 라이브 RestClient 경로를 우회해 diff·상세 로직만 고정한다
 * (collect FixtureRawSnapshotSource 선례). 파싱 규칙은 운영과 동일한 {@link ExperienceRow#fromJson}을 재사용한다.
 */
public final class ExperienceFixtures {

    private static final String FIXTURE_CLASSPATH = "/experience_fixture.json";

    private ExperienceFixtures() {
    }

    /** 픽스처 전체 52행을 원본 순서로 반환. */
    public static List<ExperienceRow> rows(ObjectMapper objectMapper) {
        try (InputStream in = ExperienceFixtures.class.getResourceAsStream(FIXTURE_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("experience_fixture 리소스 없음: " + FIXTURE_CLASSPATH);
            }
            JsonNode data = objectMapper.readTree(in).get("data");
            if (data == null || !data.isArray()) {
                throw new IllegalStateException("experience_fixture 형식 오류: data 배열 없음");
            }
            List<ExperienceRow> out = new ArrayList<>();
            for (JsonNode node : data) {
                out.add(ExperienceRow.fromJson(node));
            }
            return out;
        } catch (IOException ex) {
            throw new IllegalStateException("experience_fixture 읽기 실패", ex);
        }
    }
}
