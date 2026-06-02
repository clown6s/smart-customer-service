package com.shopmall.cs.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 分布式限流器 — 滑动窗口（Sorted Set）
 *
 * 算法说明：
 *   每个请求以 UUID 为 member、当前时间戳（毫秒）为 score 写入 ZSET。
 *   每次请求先移除窗口外的旧记录，再统计当前窗口内数量。
 *   Lua 脚本保证「判断 + 写入」原子性。
 *
 * fail-open 修复：
 *   Redis 异常时不再无条件放行，而是启用 in-memory 兜底限流。
 *   兜底基于 ConcurrentHashMap + AtomicLong 简单计数，固定窗口，
 *   精度略低但不会完全失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${cs.rate-limit:.max-requests:10}")
    private int maxRequests;

    @Value("${cs.rate-limit:.window-seconds:60}")
    private int windowSeconds;

    // ── Lua 脚本：滑动窗口（Sorted Set）───────────────────────
    // KEYS[1] = smart-cs:rate:user:{userId}
    // ARGV[1] = 窗口大小（毫秒）
    // ARGV[2] = 最大请求数
    // ARGV[3] = 当前时间戳（毫秒，用于 ZADD 的 score）
    // 返回：1 = 允许，0 = 超限
    // member 用 nowMs .. "-" .. random(1,1000000) 保证唯一
    private static final String LUA_SCRIPT = """
            local key      = KEYS[1]
            local window_ms = tonumber(ARGV[1])
            local max_req   = tonumber(ARGV[2])
            local now_ms    = tonumber(ARGV[3])
            local member    = tostring(now_ms) .. "-" .. tostring(math.random(1000000))

            -- 1. 删除窗口外的旧记录
            redis.call('ZREMRANGEBYSCORE', key, 0, now_ms - window_ms)

            -- 2. 统计当前窗口内请求数
            local cnt = redis.call('ZCARD', key)

            if cnt >= max_req then
                return 0
            end

            -- 3. 写入本次请求
            redis.call('ZADD', key, now_ms, member)
            redis.call('EXPIRE', key, math.ceil(window_ms / 1000) + 10)

            return 1
            """;

    private final DefaultRedisScript<Long> script =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    // ── Redis 不可用时的 in-memory 兜底限流 ─────────────────
    // key = userId (匿名用户共用 "anonymous")
    // value = 固定窗口计数器（秒级）
    private final ConcurrentHashMap<String, InMemoryCounter> memCounters =
            new ConcurrentHashMap<>();

    private static class InMemoryCounter {
        final AtomicLong count = new AtomicLong(0);
        volatile long windowStart = System.currentTimeMillis();

        boolean tryAcquire(int maxRequests, int windowSeconds) {
            long now = System.currentTimeMillis();
            // 窗口过期，重置
            if (now - windowStart > windowSeconds * 1000L) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }

    /**
     * 检查是否允许请求
     *
     * @param userId 用户唯一标识（null/空 视为 anonymous）
     * @return true=允许，false=超限
     */
    public boolean allowRequest(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }

        String key = "smart-cs:rate:user:" + userId;

        // ── 尝试 Redis 滑动窗口 ──────────────────────────────
        try {
            long nowMs = Instant.now().toEpochMilli();
            Long result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(windowSeconds * 1000L),
                    String.valueOf(maxRequests),
                    String.valueOf(nowMs)
            );

            if (result != null) {
                boolean allowed = result == 1L;
                if (!allowed) {
                    log.warn("[RateLimit] 请求超限, userId={}, window={}s, max={}",
                            userId, windowSeconds, maxRequests);
                }
                return allowed;
            }
            // result == null 说明 Lua 执行异常，走降级
            log.warn("[RateLimit] Lua 返回 null，启用 in-memory 兜底, userId={}", userId);

        } catch (Exception e) {
            log.error("[RateLimit] Redis 异常，启用 in-memory 兜底, userId={}, err={}",
                      userId, e.getMessage());
        }

        // ── 降级：in-memory 固定窗口兜底 ───────────────────
        return memCounters
                .computeIfAbsent(userId, k -> new InMemoryCounter())
                .tryAcquire(maxRequests, windowSeconds);
    }
}
