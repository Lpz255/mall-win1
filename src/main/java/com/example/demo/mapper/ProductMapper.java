package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}