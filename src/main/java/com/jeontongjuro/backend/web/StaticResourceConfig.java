package com.jeontongjuro.backend.web;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Immutable backend-owned brewery images are safe to cache at the browser/CDN layer.
 * The URL is kept stable so existing frontend clients do not need a contract change.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Duration BREWERY_IMAGE_CACHE_DURATION = Duration.ofDays(30);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/recommended-courses/**")
                .addResourceLocations("classpath:/static/recommended-courses/")
                .setCacheControl(CacheControl.maxAge(BREWERY_IMAGE_CACHE_DURATION).cachePublic())
                .resourceChain(true);
    }
}
