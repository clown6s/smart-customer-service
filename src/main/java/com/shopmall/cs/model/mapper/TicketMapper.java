package com.shopmall.cs.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopmall.cs.model.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
