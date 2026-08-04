package com.jeontongjuro.backend.global;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("전통주로 Backend API")
                        .description("전통주로 서비스의 백엔드 API 명세입니다.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JT_SESSION")
                                .description("로그인 완료 시 서버가 발급하는 HttpOnly 세션 쿠키")));
    }

    @Bean
    GroupedOpenApi serviceApi() {
        return GroupedOpenApi.builder()
                .group("service-api")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
