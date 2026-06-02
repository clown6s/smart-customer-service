package com.shopmall.cs.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字符")
    private String message;

    @Size(max = 64, message = "session_id不能超过64字符")
    private String sessionId;

    @Size(max = 64, message = "user_id不能超过64字符")
    private String userId;
}
