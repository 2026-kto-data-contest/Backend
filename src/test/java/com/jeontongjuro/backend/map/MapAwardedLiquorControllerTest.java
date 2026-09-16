package com.jeontongjuro.backend.map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.global.web.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MapAwardedLiquorControllerTest {

    @Mock
    private MapAwardedLiquorService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MapAwardedLiquorController(service)).build();
    }

    @Test
    void returnsAwardedLiquorCards() throws Exception {
        MapAwardedLiquorResponse card = new MapAwardedLiquorResponse(
                441, "우곡생주", "BRW-001", "해창주조장", "대상", List.of(),
                null, null, "750ml", "전남 해남군", null, null);
        given(service.list(0, 4)).willReturn(PageResponse.of(List.of(card), 0, 4, 1));

        mockMvc.perform(get("/api/v1/map/awarded-liquors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("우곡생주"))
                .andExpect(jsonPath("$.content[0].breweryName").value("해창주조장"))
                .andExpect(jsonPath("$.content[0].awardBadge").value("대상"));
    }

    @Test
    void forwardsPageParameters() throws Exception {
        given(service.list(2, 10)).willReturn(PageResponse.of(List.of(), 2, 10, 0));

        mockMvc.perform(get("/api/v1/map/awarded-liquors")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }
}
