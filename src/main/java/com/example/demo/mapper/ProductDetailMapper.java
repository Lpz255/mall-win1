package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ProductDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品详情 Mapper。
 */
@Mapper
public interface ProductDetailMapper extends BaseMapper<ProductDetail> {
}
