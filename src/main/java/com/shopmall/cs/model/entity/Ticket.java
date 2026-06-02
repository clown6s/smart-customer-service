package com.shopmall.cs.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tickets")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String subject;
    private String description;
    private String status;
    private String priority;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
