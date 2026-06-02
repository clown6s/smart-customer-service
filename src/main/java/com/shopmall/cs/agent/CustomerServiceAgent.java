package com.shopmall.cs.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI Service - LangChain4j 核心
 * 通过 @MemoryId 自动按用户隔离记忆
 * 通过 @Tool 自动注册工具
 * 启动时构建一次，避免每次请求重复创建代理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceAgent {

    private final ChatModel chatModel;
    private final Tools tools;

    @Value("${cs.session.max-history:10}")
    private int maxHistory;

    private CustomerService assistant;

    /**
     * 启动时构建 AiServices 实例（只构建一次）
     */
    @PostConstruct
    public void init() {
        // ChatMemoryProvider + @MemoryId: 按 memoryId 隔离多用户对话
        this.assistant = AiServices.builder(CustomerService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(maxHistory))
                .tools(tools)
                .build();

        log.info("[Agent] CustomerServiceAgent initialized");
    }

    /**
     * 对话 - 自动管理记忆和工具调用
     *
     * @param userId  用户ID（作为 memoryId 隔离对话）
     * @param message 用户消息
     * @return AI 回复
     */
    public String chat(String userId, String message) {
        log.info("[Agent] chat, userId={}, msgLen={}", userId, message.length());
        // 将 userId 注入消息，让 LLM 知道当前用户是谁，直接调用工具
        String enrichedMessage = "[当前用户ID: " + userId + "]\n" + message;
        return assistant.chat(userId, enrichedMessage);
    }

    /**
     * AI Service 接口定义
     * LangChain4j 动态代理实现
     * @MemoryId 自动按 userId 隔离对话记忆
     */
    public interface CustomerService {

        @SystemMessage("""
                你是电商智能客服"小助手"。

                ## 行为规范
                - 用中文简洁回复，态度友好专业
                - 能查询订单、用户信息、退款进度，创建售后工单
                - 需要查数据时务必用工具，不要编造信息
                - 模糊表述如"我的订单"时，先用queryUserOrders查列表
                - 回复中用脱敏后的手机号和邮箱
                - 用户消息开头会带 [当前用户ID: xxx]，直接用这个ID调用工具，不要再问用户要ID

                ## 工具使用
                你可以使用以下工具来帮助用户：
                1. queryUserOrders - 根据用户ID查询该用户所有订单列表
                2. queryOrderByNo - 根据订单号查询订单详情
                3. queryUserInfo - 根据用户ID查询用户信息
                4. createTicket - 创建售后工单
                5. queryRefundStatus - 根据订单号查询退款状态

                重要规则：当用户说"我的订单"、"我的退款"、"查下我的"等模糊表述时，优先使用 queryUserOrders 查出订单列表，再根据结果回答。
                """)
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }
}
