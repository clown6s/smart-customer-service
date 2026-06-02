package com.shopmall.cs.emotion;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 情绪检测 - 关键词匹配负面情绪
 * 检测到愤怒情绪时追加转人工提示
 */
@Component
public class EmotionDetector {

    @Value("${cs.emotion.enabled:true}")
    private boolean enabled;

    /** 轻度负面 - 表达不满但还没爆发 */
    private static final List<String> MILD_KEYWORDS = List.of(
            "不满", "差评", "失望", "太慢了", "等太久了", "再也不",
            "太差了", "不好用", "不满意", "体验差"
    );

    /** 中度负面 - 明确表达愤怒 */
    private static final List<String> MODERATE_KEYWORDS = List.of(
            "垃圾", "骗人", "坑人", "骗钱", "投诉", "举报",
            "什么破", "太烂了", "无语", "黑心", "骗子", "欺诈"
    );

    /** 严重负面 - 脏话、极端情绪 */
    private static final List<String> SEVERE_KEYWORDS = List.of(
            "他妈", "妈的", "傻逼", "狗屎", "滚", "废物", "混蛋",
            "恶心", "去死", "白痴", "蠢", "神经病"
    );

    /**
     * 检测情绪
     */
    public EmotionResult detect(String message) {
        if (!enabled || message == null) return EmotionResult.none();

        String lowerMsg = message.toLowerCase();

        for (String kw : SEVERE_KEYWORDS) {
            if (lowerMsg.contains(kw)) {
                return EmotionResult.of("severe",
                        "非常抱歉给您带来了不好的体验，我已为您标记为紧急工单，建议您直接联系人工客服处理。",
                        true);
            }
        }

        for (String kw : MODERATE_KEYWORDS) {
            if (lowerMsg.contains(kw)) {
                return EmotionResult.of("moderate",
                        "非常抱歉让您不满意，如果问题未能解决，您可以回复「转人工」联系人工客服。",
                        true);
            }
        }

        for (String kw : MILD_KEYWORDS) {
            if (lowerMsg.contains(kw)) {
                return EmotionResult.of("mild",
                        "抱歉给您带来不便，我会尽力帮您解决问题。",
                        false);
            }
        }

        return EmotionResult.none();
    }

    @Data
    public static class EmotionResult {
        private String level;      // none, mild, moderate, severe
        private String suggestion; // 追加建议
        private boolean transferHuman; // 是否建议转人工

        public static EmotionResult none() {
            EmotionResult r = new EmotionResult();
            r.level = "none";
            r.suggestion = null;
            r.transferHuman = false;
            return r;
        }

        public static EmotionResult of(String level, String suggestion, boolean transferHuman) {
            EmotionResult r = new EmotionResult();
            r.level = level;
            r.suggestion = suggestion;
            r.transferHuman = transferHuman;
            return r;
        }
    }
}
