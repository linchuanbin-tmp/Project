package com.agent.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())  // 禁用 CSRF（关键！）
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()  // 允许所有请求（Gateway只做转发，鉴权在下游服务）
                );
        return http.build();
    }
}