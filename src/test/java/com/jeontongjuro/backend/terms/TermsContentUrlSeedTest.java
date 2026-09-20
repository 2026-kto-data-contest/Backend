package com.jeontongjuro.backend.terms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TermsContentUrlSeedTest {

    private static final Map<String, String> EXPECTED_URLS = Map.of(
            "SERVICE_USE", "https://nonstop-platinum-949.notion.site/3e0a70bfeb5d804e8579ffe9e5cebc34?source=copy_link",
            "PRIVACY", "https://nonstop-platinum-949.notion.site/3e0a70bfeb5d806d9a9fe4e8e2b32788?source=copy_link",
            "LOCATION", "https://nonstop-platinum-949.notion.site/3e0a70bfeb5d80b5a0a6df4f9935eb8d?source=copy_link",
            "MARKETING", "https://nonstop-platinum-949.notion.site/3e0a70bfeb5d80c2a6e0e1d92f443f57?source=copy_link");

    @Test
    void schemaSeedContainsActualDocumentUrlForEveryTerm() throws IOException {
        String schema = readSchema();

        assertThat(schema).contains("ON CONFLICT (code, version) DO UPDATE SET");
        EXPECTED_URLS.forEach((code, url) -> assertThat(schema)
                .as("contentUrl for %s", code)
                .contains(url));
        assertThat(schema).doesNotContain("'/terms/service-use'")
                .doesNotContain("'/terms/privacy'")
                .doesNotContain("'/terms/location'")
                .doesNotContain("'/terms/marketing'");
    }

    private String readSchema() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("schema.sql resource").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
