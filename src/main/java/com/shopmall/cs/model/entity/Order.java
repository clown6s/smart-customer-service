package com.shopmall.cs.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String status;
    private BigDecimal amount;
    private String productName;
    private String refundStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
