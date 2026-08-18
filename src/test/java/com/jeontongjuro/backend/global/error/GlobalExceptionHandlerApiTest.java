package com.jeontongjuro.backend.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.global.web.PageResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공통 예외 처리(GlobalExceptionHandler) 인수 검증 — 특히 일반 500 핸들러(부채 #22)와,
 * 그 catch-all이 스프링 프레임워크 4xx 예외를 삼켜 500으로 바꾸지 않는지(회귀 방지)를 본다.
 * <p>
 * 서비스가 예기치 못한 예외를 던지도록 {@link MockitoBean}으로 대체해, 웹 스택 전체(컨트롤러·어드바이스·직렬화)를
 * 거친 실제 응답을 확인한다. DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다(컨텍스트 부팅에 DB 필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 예외 처리 verify 스킵")
class GlobalExceptionHandlerApiTest {

    /** 응답 바디에 새어 나오면 안 되는 내부 예외 메시지(정보 노출 검증용 마커). */
    private static final String SECRET_MARKER = "boom-내부오류-xyz-9174";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BreweryQueryService breweryQueryService;

    @Test
    @DisplayName("서비스가 RuntimeException → 500 + {code,message} 2필드(INTERNAL_SERVER_ERROR), 내부정보 미노출")
    void unexpectedExceptionYields500Contract() throws Exception {
        given(breweryQueryService.search(any(), anyInt(), anyInt()))
                .willThrow(new RuntimeException(SECRET_MARKER));

        MvcResult result = mockMvc.perform(get("/api/v1/breweries"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.*", hasSize(2)))   // code·message 딱 2필드(timestamp·path·trace 없음)
                .andReturn();

        // 🔴 스택트레이스·예외 클래스명·원 메시지가 바디에 노출되지 않는다
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(SECRET_MARKER);
        assertThat(body).doesNotContain("RuntimeException");
        assertThat(body).doesNotContain("java.");
        assertThat(body).doesNotContain("Exception");
    }

    @Test
    @DisplayName("프레임워크 4xx(협상 불가 Accept)는 500으로 바뀌지 않는다 — catch-all 재던짐 가드")
    void springFrameworkClientErrorIsNotSwallowedInto500() throws Exception {
        // 컨트롤러가 정상 바디를 반환하도록 스텁(널이면 협상 자체가 생략돼 200이 된다).
        given(breweryQueryService.search(any(), anyInt(), anyInt()))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0L));

        // 서버가 만들 수 없는 타입(application/xml)을 Accept로 주면 스프링이 406(HttpMediaTypeNotAcceptable)을 낸다.
        // 이는 org.springframework.web.ErrorResponse 이므로 catch-all이 삼키지 않고 재던져 4xx가 보존돼야 한다.
        mockMvc.perform(get("/api/v1/breweries").accept(MediaType.APPLICATION_XML))
                .andExpect(status().is4xxClientError());
    }
}
