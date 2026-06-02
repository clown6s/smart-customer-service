package com.shopmall.cs.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String reply;
    private String sessionId;
    /** 是否命中 FAQ 快速回复 */
    private boolean faqHit;
    /** 是否检测到负面情绪 */
    private boolean emotionDetected;
    /** 是否触发了工具调用 */
    private boolean toolCalled;
}
