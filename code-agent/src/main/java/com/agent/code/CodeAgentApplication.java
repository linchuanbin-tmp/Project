package com.agent.code;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Code Agent 启动类
 * <p>
 * 基于 ONNX Runtime 的本地 SQL 生成与白名单校验服务。
 * 端口 8084，通过 Gateway (8080) 统一路由。
 *
 * @author Lin Chuanbin
 */
@SpringBootApplication
@EnableScheduling
public class CodeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAgentApplication.class, args);
    }
}
