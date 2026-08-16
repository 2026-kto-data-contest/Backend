package com.jeontongjuro.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// datasource(PostgreSQL 단일) 도입으로 컨텍스트 기동에 로컬 PG가 필요해짐 — 미기동 시 명시적 스킵
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
		disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 컨텍스트 로드 테스트 스킵")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
