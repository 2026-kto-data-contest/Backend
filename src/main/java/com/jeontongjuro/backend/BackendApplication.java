package com.jeontongjuro.backend;

import com.jeontongjuro.backend.auth.config.AppProperties;
import com.jeontongjuro.backend.auth.kakao.KakaoProperties;
import com.jeontongjuro.backend.security.session.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({AuthProperties.class, KakaoProperties.class, AppProperties.class})
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
