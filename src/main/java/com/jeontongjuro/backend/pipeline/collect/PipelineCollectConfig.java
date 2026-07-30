package com.jeontongjuro.backend.pipeline.collect;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CollectProperties.class)
public class PipelineCollectConfig {
}
