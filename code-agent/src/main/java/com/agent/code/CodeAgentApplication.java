package com.agent.code;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Code Agent entry point
 * <p>
 * Local SQL generation and whitelist validation service backed by ONNX Runtime / LLM API.
 * Runs on port 8084, routed through Gateway (8080).
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
