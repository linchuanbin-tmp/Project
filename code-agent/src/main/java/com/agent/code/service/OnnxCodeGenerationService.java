package com.agent.code.service;

import com.agent.code.config.CodeAgentProperties;
import com.agent.code.dto.CodeGenerationRequest;
import com.agent.code.dto.CodeGenerationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM 推理 SQL 生成服务
 * <p>
 * 调用 Python 推理服务器 (port 8090)，底层使用 LLM API。
 * 当 code-agent.onnx.enabled=true 时自动启用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "code-agent.onnx.enabled", havingValue = "true")
public class OnnxCodeGenerationService implements CodeGenerationService {

    private final CodeAgentProperties codeAgentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public CodeGenerationResponse generateSQL(CodeGenerationRequest request) {
        String question = request.getQuestion();
        log.info("🤖 LLM 推理: {}", question);

        try {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of("question", question));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(codeAgentProperties.getOnnx().getServerUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(resp.body());
                String sql = json.get("sql").asText();
                String method = json.has("method") ? json.get("method").asText() : "LLM";

                if (sql == null || sql.isBlank()) {
                    return fail(question, "LLM 返回空 SQL");
                }

                log.info("✅ LLM SQL ({}): {}", method, sql);
                return CodeGenerationResponse.builder()
                        .success(true).sql(sql).question(question)
                        .inferenceMethod(method).whitelistPassed(null).build();
            }
            return fail(question, "推理服务返回 HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.error("❌ LLM 推理失败", e);
            return fail(question, e.getMessage());
        }
    }

    @Override
    public String getInferenceMethod() { return "LLM"; }

    private CodeGenerationResponse fail(String q, String err) {
        return CodeGenerationResponse.builder()
                .success(false).question(q).inferenceMethod("LLM")
                .errorMessage(err).build();
    }
}
