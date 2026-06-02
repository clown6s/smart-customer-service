package com.shopmall.cs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmall.cs.model.entity.ChatHistory;
import com.shopmall.cs.model.mapper.ChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 会话管理 — 对话历史异步持久化 + 自动过期清理
 * 使用 @Async 异步写入，不阻塞对话主链
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatHistoryMapper chatHistoryMapper;

    @Value("${cs.session.expire-hours:24}")
    private int expireHours;

    /** 异步保存用户消息 */
    @Async
    public void saveUserMessage(String sessionId, String userId, String content) {
        saveHistory(sessionId, userId, "user", content);
    }

    /** 异步保存助手回复 */
    @Async
    public void saveAssistantMessage(String sessionId, String userId, String content) {
        saveHistory(sessionId, userId, "assistant", content);
    }

    private void saveHistory(String sessionId, String userId, String role, String content) {
        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setUserId(userId);
            history.setRole(role);
            // 截断超长内容，防止 DB 异常（TEXT 列通常无硬限制，但 > 4000 字无实际价值）
            history.setContent(content.length() > 4000 ? content.substring(0, 4000) : content);
            history.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(history);
        } catch (Exception e) {
            // 历史记录写失败不影响主链（仅记日志）
            log.error("[Session] 保存对话历史失败, sessionId={}, role={}", sessionId, role, e);
        }
    }

    /**
     * 每小时清理过期对话历史（fixedDelay 避免上一次执行未完成时并发触发）
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanExpiredSessions() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(expireHours);
            int deleted = chatHistoryMapper.delete(
                    new LambdaQueryWrapper<ChatHistory>()
                            .lt(ChatHistory::getCreatedAt, cutoff)
            );
            if (deleted > 0) {
                log.info("[Session] 清理过期历史 {} 条, expireHours={}", deleted, expireHours);
            }
        } catch (Exception e) {
            log.error("[Session] 清理过期历史失败", e);
        }
    }
}
