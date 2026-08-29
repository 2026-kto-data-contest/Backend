package com.jeontongjuro.backend.course;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import com.jeontongjuro.backend.global.error.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendedCourseControllerTest {

    private RecommendedCourseService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(RecommendedCourseService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendedCourseController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCourseContract() throws Exception {
        given(service.findByBreweryId("BRW-001")).willReturn(new RecommendedCourseResponse(
                "BRW-001", "갈기산 양조장 코스", "충북 영동", "BRW-001",
                List.of(new CourseStopResponse(1, CourseStopType.BREWERY, "BRW-001", "갈기산",
                        "충북 영동군", new BigDecimal("36.0"), new BigDecimal("127.0"),
                        0, null, "여행의 시작", "양조장", null, null, null, List.of(), List.of()))));

        mockMvc.perform(get("/api/v1/breweries/BRW-001/recommended-course"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value("BRW-001"))
                .andExpect(jsonPath("$.centerBreweryId").value("BRW-001"))
                .andExpect(jsonPath("$.stops[0].type").value("BREWERY"))
                .andExpect(jsonPath("$.stops[0].distanceMeters").value(0));
    }

    @Test
    void missingBreweryUsesCommonErrorContract() throws Exception {
        given(service.findByBreweryId("BRW-999"))
                .willThrow(new BreweryNotFoundException("양조장을 찾을 수 없습니다: BRW-999"));

        mockMvc.perform(get("/api/v1/breweries/BRW-999/recommended-course"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BREWERY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("양조장을 찾을 수 없습니다: BRW-999"));
    }
}
