package com.jeontongjuro.backend.recommendation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.global.web.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendedCourseListControllerTest {

    private RecommendedCourseListService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(RecommendedCourseListService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendedCourseListController(service)).build();
    }

    @Test
    void anonymousCanReadPagedCourseCards() throws Exception {
        given(service.list(null, 0, 20)).willReturn(PageResponse.of(List.of(
                new RecommendedCourseCardResponse("BRW-001", null, "충북 영동", "갈기산 코스")),
                0, 20, 1));

        mockMvc.perform(get("/api/v1/recommendations/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].courseId").value("BRW-001"))
                .andExpect(jsonPath("$.content[0].regionLabel").value("충북 영동"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
