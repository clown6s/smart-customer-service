package com.shopmall.cs.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.model-name:deepseek-chat}")
    private String modelName;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${deepseek.max-tokens:1024}")
    private int maxTokens;

    @Value("${deepseek.temperature:0.7}")
    private double temperature;

    @Value("${deepseek.max-retries:2}")
    private int maxRetries;

    /** 生产默认关闭，dev profile 通过 application-dev.yml 覆盖 */
    @Value("${deepseek.log-requests:false}")
    private boolean logRequests;

    @Value("${deepseek.log-responses:false}")
    private boolean logResponses;

    /**
     * ChatModel — DeepSeek 兼容 OpenAI API
     */
    @Bean
    public ChatModel chatModel() {
        log.info("[LangChain4j] 初始化 ChatModel: baseUrl={}, model={}, timeout={}s, maxRetries={}",
                baseUrl, modelName, timeoutSeconds, maxRetries);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxTokens(maxTokens)
                .temperature(temperature)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
