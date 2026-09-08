package com.jeontongjuro.backend.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.global.error.GlobalExceptionHandler;
import com.jeontongjuro.backend.global.web.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MapPlaceSearchApiTest {

    private MockMvc mockMvc;
    private MapPlaceService mapPlaceService;

    @BeforeEach
    void setUp() {
        mapPlaceService = mock(MapPlaceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MapPlaceSearchController(mapPlaceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 공개검색API가서비스인자와응답계약을연결한다() throws Exception {
        MapPlaceResponse place = new MapPlaceResponse("BRW-001", "안동 양조장",
                MapPlaceCategory.BREWERY, "양조장", 1.2, "경북 안동시", null,
                new BigDecimal("36.5"), new BigDecimal("128.7"), null);
        given(mapPlaceService.search(eq("안동"), isNull(), any(), any(), eq(0), eq(20)))
                .willReturn(PageResponse.of(List.of(place), 0, 20, 1));

        mockMvc.perform(get("/api/v1/map/places/search")
                        .param("keyword", "안동")
                        .param("latitude", "36.4")
                        .param("longitude", "128.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placeId").value("BRW-001"))
                .andExpect(jsonPath("$.content[0].category").value("BREWERY"))
                .andExpect(jsonPath("$.content[0].distance").value(1.2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 서비스검증실패는공통400계약으로반환한다() throws Exception {
        given(mapPlaceService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new InvalidQueryParameterException("keyword는 공백 제거 후 1~50자여야 합니다."));

        mockMvc.perform(get("/api/v1/map/places/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }
}
