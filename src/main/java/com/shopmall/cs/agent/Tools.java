package com.shopmall.cs.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmall.cs.model.entity.Order;
import com.shopmall.cs.model.entity.Ticket;
import com.shopmall.cs.model.entity.User;
import com.shopmall.cs.model.mapper.OrderMapper;
import com.shopmall.cs.model.mapper.TicketMapper;
import com.shopmall.cs.model.mapper.UserMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 可调用的工具集
 * LangChain4j 通过 @Tool 注解自动注册，反射解析参数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Tools {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final TicketMapper ticketMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 订单相关 ====================

    @Tool("根据用户ID查询该用户的所有订单列表")
    public String queryUserOrders(String userId) {
        log.info("[Tool] queryUserOrders, userId={}", userId);
        Long uid = safeParseLong(userId);
        if (uid == null) return "无效的用户ID";

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, uid)
                        .orderByDesc(Order::getCreatedAt)
        );

        if (orders.isEmpty()) {
            return "用户 " + userId + " 暂无订单";
        }

        return orders.stream()
                .map(o -> String.format("订单号: %s, 状态: %s, 金额: %.2f元, 商品: %s, 退款状态: %s, 下单时间: %s",
                        o.getOrderNo(), statusCn(o.getStatus()), o.getAmount(),
                        o.getProductName() != null ? o.getProductName() : "-",
                        o.getRefundStatus() != null ? refundStatusCn(o.getRefundStatus()) : "无",
                        o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "-"))
                .collect(Collectors.joining("\n"));
    }

    @Tool("根据订单号查询订单详情")
    public String queryOrderByNo(String orderNo) {
        log.info("[Tool] queryOrderByNo, orderNo={}", orderNo);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) return "未找到订单 " + orderNo;

        return String.format("订单号: %s\n状态: %s\n金额: %.2f元\n商品: %s\n退款状态: %s\n下单时间: %s",
                order.getOrderNo(), statusCn(order.getStatus()), order.getAmount(),
                order.getProductName() != null ? order.getProductName() : "-",
                order.getRefundStatus() != null ? refundStatusCn(order.getRefundStatus()) : "无",
                order.getCreatedAt() != null ? order.getCreatedAt().format(FMT) : "-");
    }

    @Tool("根据订单号查询退款状态和进度")
    public String queryRefundStatus(String orderNo) {
        log.info("[Tool] queryRefundStatus, orderNo={}", orderNo);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) return "未找到订单 " + orderNo;

        if (order.getRefundStatus() == null) {
            return "订单 " + orderNo + " 没有退款记录";
        }

        return String.format("订单号: %s\n退款状态: %s\n商品: %s\n退款金额: %.2f元",
                order.getOrderNo(), refundStatusCn(order.getRefundStatus()),
                order.getProductName() != null ? order.getProductName() : "-",
                order.getAmount());
    }

    // ==================== 用户相关 ====================

    @Tool("根据用户ID查询用户基本信息")
    public String queryUserInfo(String userId) {
        log.info("[Tool] queryUserInfo, userId={}", userId);
        Long uid = safeParseLong(userId);
        if (uid == null) return "无效的用户ID";

        User user = userMapper.selectById(uid);
        if (user == null) return "未找到用户 " + userId;

        return String.format("用户ID: %d\n用户名: %s\n手机号: %s\n邮箱: %s\n注册时间: %s",
                user.getId(), user.getUsername(),
                maskPhone(user.getPhone()),
                maskEmail(user.getEmail()),
                user.getCreatedAt() != null ? user.getCreatedAt().format(FMT) : "-");
    }

    // ==================== 工单相关 ====================

    @Tool("创建售后工单")
    public String createTicket(String userId, String description, String orderNo) {
        log.info("[Tool] createTicket, userId={}, orderNo={}", userId, orderNo);
        Long uid = safeParseLong(userId);
        if (uid == null) return "无效的用户ID";

        Ticket ticket = new Ticket();
        ticket.setUserId(uid);
        ticket.setSubject(description.length() > 100 ? description.substring(0, 100) : description);
        ticket.setDescription(description);
        ticket.setOrderNo(orderNo);
        ticket.setStatus("pending");
        ticket.setPriority("normal");

        try {
            ticketMapper.insert(ticket);
            return String.format("工单已创建成功！工单ID: %d，我们会尽快处理，请耐心等待。", ticket.getId());
        } catch (Exception e) {
            log.error("[Tool] 创建工单失败", e);
            return "创建工单失败，请稍后再试";
        }
    }

    // ==================== 工具方法 ====================

    private Long safeParseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String statusCn(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "pending" -> "待处理";
            case "paid" -> "已付款";
            case "shipped" -> "已发货";
            case "delivered" -> "已送达";
            case "cancelled" -> "已取消";
            case "refunded" -> "已退款";
            default -> status;
        };
    }

    private String refundStatusCn(String status) {
        if (status == null) return "无";
        return switch (status) {
            case "pending" -> "退款申请中";
            case "processing" -> "退款处理中";
            case "completed" -> "退款已完成";
            case "rejected" -> "退款已拒绝";
            default -> status;
        };
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone != null ? "***" : "-";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email != null ? "***" : "-";
        int atIndex = email.indexOf("@");
        if (atIndex <= 3) return "***" + email.substring(atIndex);
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }
}
