package com.shopmall.cs.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopmall.cs.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
