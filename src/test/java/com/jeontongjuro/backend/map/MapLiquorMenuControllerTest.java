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

class MapLiquorMenuControllerTest {
    @Mock private MapLiquorMenuService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MapLiquorMenuController(service)).build();
    }

    @Test
    void returnsFlavorAndLiquorTypeMenus() throws Exception {
        given(service.menus()).willReturn(List.of(
                new MapLiquorMenuResponse("FRESH", "상큼함", "FLAVOR"),
                new MapLiquorMenuResponse("YAKJU", "약주", "LIQUOR_TYPE")));

        mockMvc.perform(get("/api/v1/map/liquor-menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("상큼함"))
                .andExpect(jsonPath("$[1].kind").value("LIQUOR_TYPE"));
    }

    @Test
    void returnsFilteredLiquorCards() throws Exception {
        MapLiquorMenuItemResponse item = new MapLiquorMenuItemResponse(
                441, "우곡생주", "BRW-001", "해창주조장", List.of(), List.of(),
                null, null, "750ml", "전남 해남군", null, null, null);
        given(service.products("FRESH", 0, 4)).willReturn(PageResponse.of(List.of(item), 0, 4, 1));

        mockMvc.perform(get("/api/v1/map/liquor-menus/FRESH/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("우곡생주"))
                .andExpect(jsonPath("$.size").value(4));
    }
}
