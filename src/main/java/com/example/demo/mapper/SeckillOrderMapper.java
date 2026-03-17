package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper。
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}