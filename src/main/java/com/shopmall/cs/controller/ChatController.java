package com.shopmall.cs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmall.cs.config.TraceIdFilter;
import com.shopmall.cs.model.dto.ApiResponse;
import com.shopmall.cs.model.dto.ChatRequest;
import com.shopmall.cs.model.dto.ChatResponse;
import com.shopmall.cs.ratelimit.RateLimiter;
import com.shopmall.cs.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 智能客服 API
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Qualifier("sseExecutor")
    private final ExecutorService sseExecutor;

    /**
     * JSON 对话接口（支持 Function Calling）
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";

        if (!rateLimiter.allowRequest(userId)) {
            log.warn("[Chat] 请求限流, userId={}", userId);
            return ResponseEntity.status(429)
                    .body(ApiResponse.error(429, "请求过于频繁，请稍后再试"));
        }

        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * SSE 流式对话接口
     * 先获取完整结果，再逐句推送（LLM Function Calling 场景无法做真流式）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";

        if (!rateLimiter.allowRequest(userId)) {
            SseEmitter emitter = new SseEmitter(30_000L);
            try {
                emitter.send(SseEmitter.event().name("error").data("请求过于频繁，请稍后再试"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        // 在主线程捕获 MDC，传入子线程（保持 traceId 链路）
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        sseExecutor.execute(() -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                ChatResponse response = chatService.chat(request);

                String reply = response.getReply();
                // 按标点分句推送，比逐字更自然
                String[] sentences = reply.split("(?<=[。！？\n.!?])");
                for (String sentence : sentences) {
                    if (!sentence.isBlank()) {
                        emitter.send(SseEmitter.event().name("message").data(sentence));
                        Thread.sleep(30);
                    }
                }

                String metadata = objectMapper.writeValueAsString(
                        Map.of("sessionId", response.getSessionId() != null ? response.getSessionId() : "",
                               "traceId", MDC.get(TraceIdFilter.MDC_TRACE_KEY) != null
                                       ? MDC.get(TraceIdFilter.MDC_TRACE_KEY) : "")
                );
                emitter.send(SseEmitter.event().name("metadata").data(metadata));
                emitter.complete();
            } catch (IOException e) {
                log.warn("[SSE] 客户端断开连接, userId={}", userId);
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("[SSE] 流式发送失败, userId={}", userId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务异常，请稍后再试"));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            } finally {
                MDC.clear();
            }
        });

        return emitter;
    }

    /**
     * 健康检查（轻量，不走 Actuator）
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Object>> health() {
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
