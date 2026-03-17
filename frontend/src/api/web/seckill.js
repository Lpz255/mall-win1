import request from '@/api/request';

// 秒杀列表
export function getSeckillListApi(params = {}) {
  return request.get('/seckill/list', { params });
}

// 秒杀下单
export function doSeckillApi(productId) {
  return request.post(`/seckill/${productId}`);
}

