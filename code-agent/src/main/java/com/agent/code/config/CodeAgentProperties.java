package com.agent.code.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Code Agent 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "code-agent")
public class CodeAgentProperties {

    private OnnxConfig onnx = new OnnxConfig();
    private WhitelistConfig whitelist = new WhitelistConfig();
    private MetadataConfig metadata = new MetadataConfig();

    @Data
    public static class OnnxConfig {
        private String modelPath = "classpath:models/text2sql.onnx";
        private boolean enabled = false;
    }

    @Data
    public static class WhitelistConfig {
        private List<String> allowedOperations = List.of("SELECT");
        private List<String> forbiddenKeywords = List.of(
                "DROP", "DELETE", "INSERT", "UPDATE", "ALTER",
                "TRUNCATE", "CREATE", "EXEC", "EXECUTE", "UNION",
                "INTO", "LOAD_FILE", "OUTFILE", "DUMPFILE"
        );
        private int maxTablesPerQuery = 3;
        private int maxConditions = 10;
    }

    @Data
    public static class MetadataConfig {
        private int cacheTtl = 3600;
        private String cachePrefix = "code_agent:metadata:";
    }
}
