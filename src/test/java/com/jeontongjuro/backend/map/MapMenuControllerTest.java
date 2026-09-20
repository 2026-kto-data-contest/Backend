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

class MapMenuControllerTest {
    @Mock
    private MapMenuService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MapMenuController(service)).build();
    }

    @Test
    void returnsMenuCategories() throws Exception {
        given(service.menus()).willReturn(List.of(new MapMenuResponse("PAJEON", "파전")));

        mockMvc.perform(get("/api/v1/map/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].menu").value("PAJEON"))
                .andExpect(jsonPath("$[0].displayName").value("파전"));
    }

    @Test
    void returnsPlacesForMenu() throws Exception {
        MapPlaceResponse place = new MapPlaceResponse("C-1", "남도파전", MapPlaceCategory.RESTAURANT,
                "식당", null, "서울", null, null, null, null);
        given(service.places("PAJEON", 0, 20))
                .willReturn(PageResponse.of(List.of(place), 0, 20, 1));

        mockMvc.perform(get("/api/v1/map/menus/PAJEON/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placeName").value("남도파전"));
    }

    @Test
    void forwardsPagingParametersWithoutLocation() throws Exception {
        given(service.places("SASHIMI", 1, 5))
                .willReturn(PageResponse.of(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/map/menus/SASHIMI/places")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }
}
