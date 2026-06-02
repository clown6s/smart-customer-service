package com.shopmall.cs.service;

import com.shopmall.cs.agent.CustomerServiceAgent;
import com.shopmall.cs.emotion.EmotionDetector;
import com.shopmall.cs.faq.FaqMatcher;
import com.shopmall.cs.model.dto.ChatRequest;
import com.shopmall.cs.model.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 对话编排服务
 * 处理流程：限流(Controller) → FAQ → 情绪检测 → 转人工 → LLM Agent → 降级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final FaqMatcher faqMatcher;
    private final EmotionDetector emotionDetector;
    private final CustomerServiceAgent customerServiceAgent;
    private final SessionService sessionService;

    private static final String FALLBACK_REPLY =
            "抱歉，智能客服暂时无法响应，请稍后再试。如需人工服务，请回复\"转人工\"。";

    /**
     * 处理对话请求
     */
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        String userId    = request.getUserId()    != null ? request.getUserId()    : "anonymous";
        String message   = request.getMessage();

        // MDC 注入链路上下文（日志自动携带）
        MDC.put("userId", userId);
        MDC.put("sessionId", sessionId);

        try {
            // 持久化用户消息（异步，不阻塞主链）
            sessionService.saveUserMessage(sessionId, userId, message);

            // 1. FAQ 快速匹配
            String faqReply = faqMatcher.match(message);
            if (faqReply != null) {
                log.info("[Chat] FAQ命中, userId={}", userId);
                sessionService.saveAssistantMessage(sessionId, userId, faqReply);
                return ChatResponse.builder()
                        .reply(faqReply)
                        .sessionId(sessionId)
                        .faqHit(true)
                        .build();
            }

            // 2. 情绪检测
            var emotion = emotionDetector.detect(message);
            if (!"none".equals(emotion.getLevel())) {
                log.info("[Chat] 情绪检测: level={}, userId={}", emotion.getLevel(), userId);
            }

            // 3. 转人工关键词检测
            String trimmed = message.trim();
            if ("转人工".equals(trimmed) || "人工".equals(trimmed) || trimmed.contains("转人工")) {
                String reply = "好的，正在为您转接人工客服，请稍等...\n人工客服服务时间：每天 9:00-21:00";
                sessionService.saveAssistantMessage(sessionId, userId, reply);
                return ChatResponse.builder()
                        .reply(reply)
                        .sessionId(sessionId)
                        .build();
            }

            // 4. LLM Agent 调用
            String reply;
            boolean toolCalled = false;
            try {
                reply = customerServiceAgent.chat(userId, message);
                toolCalled = true; // Agent 内部可能调用工具，对外透明标记
            } catch (Exception e) {
                log.error("[Chat] LLM调用失败, userId={}, error={}", userId, e.getMessage(), e);
                reply = FALLBACK_REPLY;
            }

            // 5. 追加情绪安抚话术
            if (emotion.getSuggestion() != null) {
                reply = reply + "\n\n" + emotion.getSuggestion();
            }

            sessionService.saveAssistantMessage(sessionId, userId, reply);

            return ChatResponse.builder()
                    .reply(reply)
                    .sessionId(sessionId)
                    .emotionDetected(!"none".equals(emotion.getLevel()))
                    .toolCalled(toolCalled)
                    .build();
        } finally {
            MDC.remove("userId");
            MDC.remove("sessionId");
        }
    }
}
