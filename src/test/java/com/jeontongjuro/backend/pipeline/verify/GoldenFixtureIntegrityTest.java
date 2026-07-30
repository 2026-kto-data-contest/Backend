package com.jeontongjuro.backend.pipeline.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * verify 1종: 골든 픽스처 "파일 무결성" 검증 (DB 무관, Spring 무관).
 * (a) raw 파일 바이트 sha256 재계산 == manifest 봉인값
 * (b) data 배열 길이 == manifest actual_rows == 기대값(brewery 59 / product 1215) 3자 일치
 */
class GoldenFixtureIntegrityTest {

    private static final String GOLDEN_DIR = "/golden/";
    private static final String MANIFEST = GOLDEN_DIR + "20260728_manifest.json";

    /** 기대 행수 — 골든 봉인 시점에 확정된 값. */
    private static final Map<String, Integer> EXPECTED_ROWS = Map.of(
            "20260728_brewery_raw.json", 59,
            "20260728_product_raw.json", 1215
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("골든 raw 파일 sha256 == manifest 봉인값, 행수 3자 일치(파일 == actual_rows == 기대값)")
    void goldenFixturesMatchManifestSeal() throws IOException {
        JsonNode manifest = objectMapper.readTree(readBytes(MANIFEST));
        JsonNode datasets = manifest.get("datasets");
        assertThat(datasets).as("manifest datasets 배열").isNotNull();
        assertThat(datasets.size()).isEqualTo(EXPECTED_ROWS.size());

        for (JsonNode dataset : datasets) {
            String rawFile = dataset.get("raw_file").asText();
            byte[] rawBytes = readBytes(GOLDEN_DIR + rawFile);

            // (a) 바이트 단위 sha256 == manifest 봉인값
            assertThat(sha256Hex(rawBytes))
                    .as("%s sha256", rawFile)
                    .isEqualTo(dataset.get("sha256").asText());

            // (b) 파일 data 길이 == manifest actual_rows == 기대값
            int fileRows = objectMapper.readTree(rawBytes).get("data").size();
            int manifestRows = dataset.get("actual_rows").asInt();
            Integer expectedRows = EXPECTED_ROWS.get(rawFile);
            assertThat(expectedRows).as("%s 기대 행수 정의됨", rawFile).isNotNull();
            assertThat(fileRows).as("%s 파일 행수 == manifest actual_rows", rawFile).isEqualTo(manifestRows);
            assertThat(fileRows).as("%s 파일 행수 == 기대값", rawFile).isEqualTo(expectedRows);
        }
    }

    private static byte[] readBytes(String classpathLocation) throws IOException {
        try (InputStream in = GoldenFixtureIntegrityTest.class.getResourceAsStream(classpathLocation)) {
            assertThat(in).as("클래스패스 리소스 존재: %s", classpathLocation).isNotNull();
            return in.readAllBytes();
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
