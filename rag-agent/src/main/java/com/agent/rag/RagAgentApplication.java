package com.agent.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.agent.rag.mapper")
public class RagAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAgentApplication.class, args);
    }
}
