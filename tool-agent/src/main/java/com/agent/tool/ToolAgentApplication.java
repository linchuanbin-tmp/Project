package com.agent.tool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ToolAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToolAgentApplication.class, args);
    }
}