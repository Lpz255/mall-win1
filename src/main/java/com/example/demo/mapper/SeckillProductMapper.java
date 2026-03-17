package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀商品 Mapper。
 */
@Mapper
public interface SeckillProductMapper extends BaseMapper<SeckillProduct> {
}