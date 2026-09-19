package com.jeontongjuro.backend.map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.brewery.query.MainImageResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MapRecommendedBreweryApiTest {

    @Mock
    private MapRecommendedBreweryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MapRecommendedBreweryController(service)).build();
    }

    @Test
    void returnsRecommendedBreweryCards() throws Exception {
        MapRecommendedBreweryResponse card = new MapRecommendedBreweryResponse(
                "BRW-001", "해창주조장", "전남 해남군 화산면 해창길 1",
                new BigDecimal("34.573210"), new BigDecimal("126.598120"),
                List.of(LiquorType.탁주), List.of(),
                MainImageResponse.from("https://example.com/image.jpg", "Type1"));
        given(service.recommend(null, 0, 4))
                .willReturn(PageResponse.of(List.of(card), 0, 4, 1));

        mockMvc.perform(get("/api/v1/map/recommended-breweries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].breweryId").value("BRW-001"))
                .andExpect(jsonPath("$.content[0].address").value("전남 해남군 화산면 해창길 1"))
                .andExpect(jsonPath("$.content[0].latitude").value(34.573210))
                .andExpect(jsonPath("$.content[0].liquorTypes[0]").value("탁주"));
    }

    @Test
    void forwardsPageParameters() throws Exception {
        given(service.recommend(null, 1, 8))
                .willReturn(PageResponse.of(List.of(), 1, 8, 0));

        mockMvc.perform(get("/api/v1/map/recommended-breweries")
                        .param("page", "1").param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(8));
    }
}
