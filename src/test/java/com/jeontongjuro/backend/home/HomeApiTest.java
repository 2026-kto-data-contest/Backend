package com.jeontongjuro.backend.home;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.home.dto.HomeBannerResponse;
import com.jeontongjuro.backend.home.dto.HomeBannerType;
import com.jeontongjuro.backend.home.dto.HomeBrewerySectionResponse;
import com.jeontongjuro.backend.home.dto.HomeHeaderResponse;
import com.jeontongjuro.backend.home.dto.HomeResponse;
import com.jeontongjuro.backend.home.dto.HomeViewerResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 홈 API verify 스킵")
class HomeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeService homeService;

    @Test
    void anonymousCanReadHome() throws Exception {
        HomeResponse response = new HomeResponse(
                new HomeViewerResponse(false, false),
                new HomeHeaderResponse("전통주 여행을 시작해 볼까요?"),
                new HomeBannerResponse(HomeBannerType.LOGIN, "로그인해 주세요.", "/login"),
                List.of(),
                new HomeBrewerySectionResponse("탁주", List.of()),
                new HomeBrewerySectionResponse("수도권", List.of()),
                List.of());
        given(homeService.getHome(isNull(), isNull(), isNull())).willReturn(response);

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewer.authenticated").value(false))
                .andExpect(jsonPath("$.banner.type").value("LOGIN"))
                .andExpect(jsonPath("$.recommendedCourses").isArray())
                .andExpect(jsonPath("$.liquorTypeBreweries.selectedValue").value("탁주"))
                .andExpect(jsonPath("$.regionBreweries.selectedValue").value("수도권"));
    }
}
