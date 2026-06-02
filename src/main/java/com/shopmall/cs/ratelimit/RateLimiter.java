package com.shopmall.cs.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 分布式限流器
 * 算法：固定窗口计数（Lua 脚本保证原子性）
 *
 * 原脚本 BUG 修正：
 *   原代码 execute(script, keys, windowSeconds) 只传了 1 个 ARGV，maxRequests 未传入 Redis 侧，
 *   实际判断 count > maxRequests 在 Java 侧完成，Lua 内无法自行拒绝，逻辑分散。
 *   修正后：Lua 脚本内完成所有判断，返回 1=允许 / 0=拒绝，逻辑内聚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${cs.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${cs.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /**
     * Lua 脚本（固定窗口计数）：
     * KEYS[1] = 限流 key
     * ARGV[1] = 窗口秒数
     * ARGV[2] = 最大请求数
     * 返回: 1 = 允许, 0 = 超限
     */
    private static final String LUA_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            if count > tonumber(ARGV[2]) then
                return 0
            end
            return 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    /**
     * 检查是否允许请求
     * Redis 不可用时降级放行（fail-open），并告警
     *
     * @param userId 用户唯一标识
     * @return true=允许, false=超限
     */
    public boolean allowRequest(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }

        // key 命名规范：应用名:业务:维度:值
        String key = "smart-cs:rate:user:" + userId;

        try {
            Long result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests)
            );

            if (result == null) {
                log.warn("[RateLimit] Redis 返回 null，降级放行, userId={}", userId);
                return true;
            }

            boolean allowed = result == 1L;
            if (!allowed) {
                log.warn("[RateLimit] 请求超限, userId={}, window={}s, max={}", userId, windowSeconds, maxRequests);
            }
            return allowed;
        } catch (Exception e) {
            log.error("[RateLimit] Redis 异常，降级放行, userId={}, error={}", userId, e.getMessage());
            return true;
        }
    }
}
